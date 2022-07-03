package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import it.necst.entree.Tree;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EntreeFloorplanner {
    public static final Pattern pblockCoordinates = Pattern.compile("^SLICE_X(?<xa>\\d+)Y(?<ya>\\d+):SLICE_X(?<xb>\\d+)Y(?<yb>\\d+)$");
    public static int xa;
    public static int xb;
    public static int ya;
    public static int yb;
    static final int OVERHEAD_RATIO = 1;
    static final int COL_SIZE = 1;
    static final int ROW_SIZE = 60; // row and col size defined by the fpga fabric
    static final int GROUP_NUMBER = 4; //Apriori defined number of groups
    static final int BANKS = 2; //Apriori defined number of banks
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";
    private static int getSliceNumberFromCoordinates(String coordinates){
        Matcher matcher = pblockCoordinates.matcher(coordinates);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Regex failed!");
        }
        xa = Integer.parseInt(matcher.group("xa"));
        ya = Integer.parseInt(matcher.group("ya"));
        xb = Integer.parseInt(matcher.group("xb"));
        yb = Integer.parseInt(matcher.group("yb"));
        return (yb - ya + 1) * (xb - xa + 1);
    }
    private static void createCSV(List<Tree> trees, String filename) throws IOException {
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);

        List<String> c =
                trees.stream()
                        .map(Tree::getCoordinates)
                        .distinct()
                        .collect(Collectors.toList());

        PrintWriter pw = new PrintWriter(filename);
        StringBuilder csvBody = new StringBuilder(); //TODO
        pw.write("Tree Name;EstimatorID;ClassID;Bank Number;Tree Number;Group;Slice Count;Coordinates;"); //header
        for (int i = 1; i <= GROUP_NUMBER; i++){
            pw.write("Tree / Group #" + i + ";");
        }
        pw.write("\n");
        for (Tree t : trees) { //body
            csvBody.append(t.gettName()).append(";")
                    .append(t.getEstimatorId()).append(";")
                    .append(t.getClassId()).append(";")
                    .append(t.getBankNumber()).append(";")
                    .append(t.getTreeNumber()).append(";")
                    .append(t.getGroup()).append(";")
                    .append(t.sliceCount).append(";")
                    .append(t.getCoordinates()).append(";");
            for (String s : c) {
                csvBody.append(df.format((float) t.sliceCount / (float) getSliceNumberFromCoordinates(s))).append(";");
            }
            csvBody.append("\n");
        }
        pw.write(csvBody.toString());
        pw.close();
    }
    public static void main(String[] args) throws IOException {

        int TREE_NUMBER = args.length;

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(path.replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        File tempFile;
        try {
            tempFile = File.createTempFile(SHAPES_REPORT_FILE_NAME, ".txt");
        } catch (IOException e) {
            System.out.println("Unable to create tmp file.");
            throw new RuntimeException(e);
        }

        tempFile.deleteOnExit();

        //LOOP #1 to generate the pblocks
        for (Tree t : trees) {
            String treeName = t.gettName();
            PBlockGenerator pbGen = new PBlockGenerator.Builder()
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(1.2F)
                    .setIP_NR_INSTANCES(1)
                    .build();

            HashSet<String> alreadySeen = new HashSet<>();
            int requested = pbGen.PBLOCK_COUNT;
            for(String s : pbGen.generatePBlockFromReport(t.getUtilReport(), tempFile.getAbsolutePath())){

                //Get number of slices for each tree

                t.sliceCount = getSliceNumberFromCoordinates(s);

                if(alreadySeen.contains(s)) continue;
                System.out.println(treeName + "\t" + s + "\t" + t.sliceCount); //print current state of tree TODO: use toString
                alreadySeen.add(s);
                requested--;
                if(requested == 0) break;
            }
        }

        //Sort Tree's list by number of slices
        trees.sort(Comparator.comparing(a -> a.sliceCount));

        System.out.println("\n------------------------------------------------------------------------------\n");

        //LOOP #2 to generate the final Pblocks, csv and xdc files
        PrintWriter pw = new PrintWriter("/home/locav/const.xdc");
        for (int i = TREE_NUMBER/GROUP_NUMBER - 1; i  < TREE_NUMBER; i += TREE_NUMBER/GROUP_NUMBER) {
            Tree t = trees.get(i);
            //TODO: check validity bank and tree number
            int bankNumber = i / (TREE_NUMBER/BANKS);
            int treeNumber = i / (TREE_NUMBER/GROUP_NUMBER) % BANKS;
            String treeUtilReport = t.getUtilReport();

            PBlockGenerator p = new PBlockGenerator.Builder()
                    .setGLOBAL_PBLOCK("/home/locav/global.txt")
                    .setIP_NR_INSTANCES(1)
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(1.2F) //with overhead = 1 it fails placing | 70% CLB utilization with 1.2
                    .build();

            HashSet<String> alreadySeen = new HashSet<>();
            int requested = p.PBLOCK_COUNT;
            for (String s : p.generatePBlockFromReport(treeUtilReport, tempFile.getAbsolutePath())) {
                if (alreadySeen.contains(s)) continue;
                alreadySeen.add(s);

                for (int j = i - TREE_NUMBER / GROUP_NUMBER + 1; j <= i; j++) {
                    Tree o = trees.get(j);
                    o.setCoordinates(s);
                    o.setTreeNumber(treeNumber);
                    o.setBankNumber(bankNumber);
                    o.setGroup(j / (TREE_NUMBER/GROUP_NUMBER) % GROUP_NUMBER + 1);
                }

                requested--;
                if(requested == 0) break;
            }

            pw.write("create_pblock \n" + t.gettName() +
                    "add_cells_to_pblock [get_pblocks " + t.gettName() + " ] [get_cells -quiet [list top_design_i/tree_rp_" + t.getBankNumber() + "_" + t.getTreeNumber() +"]]\n" +
                    "resize_pblock [get_pblocks " + t.gettName() +"] -add {" + t.getCoordinates() + "}\n" +
                    "set_property SNAPPING_MODE ON [get_pblocks " + t.gettName() + "]\n");
        }
        pw.close();

        for (Tree t : trees){
            System.out.println(t);
        }

        System.out.println("\n------------------------------------------------------------------------------\n");
        createCSV(trees, "/home/locav/csv_trees.csv");

    }

    //FIXME: non vengono evitati i pblocks anche se inseriti in global.txt manualmente

}


