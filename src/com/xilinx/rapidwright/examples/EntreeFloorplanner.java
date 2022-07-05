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
    static final float OVERHEAD_RATIO = 1.2F;  //with overhead = 1 it fails placing | 70% CLB utilization with overhead = 1.2
    static final int COL_SIZE = 1;
    static final int ROW_SIZE = 60; // row and col size defined by the fpga fabric
    static final int GROUP_NUMBER = 6; //Apriori defined number of groups
    static final int BANKS = 2; //Apriori defined number of banks
    static final String WORKING_DIR = "/home/locav/"; //Set the working directory
    static final String SHAPES_REPORT_FILE_NAME = "shape";
    static final String GLOBAL_PBLOCKS_FILE_NAME = "global_pblocks.txt";
    static final String CONSTRAINTS_FILE_NAME = "const.xdc";
    static final String CSV_FILE_NAME = "tree_info.csv";
    private static int getSliceNumberFromCoordinates(String coordinates){
        Matcher matcher = pblockCoordinates.matcher(coordinates);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Regex failed!");
        }
        int xa = Integer.parseInt(matcher.group("xa"));
        int ya = Integer.parseInt(matcher.group("ya"));
        int xb = Integer.parseInt(matcher.group("xb"));
        int yb = Integer.parseInt(matcher.group("yb"));
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
        StringBuilder csvBody = new StringBuilder();
        pw.write("Tree Name;EstimatorID;ClassID;Group;Coordinates;");
        for (int i = 1; i <= GROUP_NUMBER; i++){
            pw.write("Tree / Group #" + i + ";");
        }
        pw.write("\n");
        for (Tree t : trees) { //body
            csvBody.append(t.gettName()).append(";")
                    .append(t.getEstimatorId()).append(";")
                    .append(t.getClassId()).append(";")
                    .append(t.getGroup()).append(";")
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
        File globalPblocksFile = new File(WORKING_DIR + GLOBAL_PBLOCKS_FILE_NAME);
        File constraintsFile = new File(WORKING_DIR + CONSTRAINTS_FILE_NAME);
        File csvFile = new File(WORKING_DIR + CSV_FILE_NAME);
        globalPblocksFile.createNewFile();
        constraintsFile.createNewFile();
        csvFile.createNewFile();

        File tempFile;
        try {
            tempFile = File.createTempFile(SHAPES_REPORT_FILE_NAME, ".txt");

        } catch (IOException e) {
            System.out.println("Unable to create tmp file.");
            throw new RuntimeException(e);
        }
        tempFile.deleteOnExit();

        int TREE_NUMBER = args.length;

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(path.replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        //LOOP #1 to generate the pblocks
        for (Tree t : trees) {
            String treeName = t.gettName();
            PBlockGenerator pbGen = new PBlockGenerator.Builder()
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
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
        PrintWriter pw = new PrintWriter(constraintsFile.getAbsolutePath());

        for (int i = TREE_NUMBER/GROUP_NUMBER - 1; i  < TREE_NUMBER; i += TREE_NUMBER/GROUP_NUMBER) { //loop over groups
            Tree t = trees.get(i);
            String treeUtilReport = t.getUtilReport();

            PBlockGenerator p = new PBlockGenerator.Builder()
                    .setGLOBAL_PBLOCK(globalPblocksFile.getAbsolutePath())
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .build();

            HashSet<String> alreadySeen = new HashSet<>();
            int requested = p.PBLOCK_COUNT;
            for (String s : p.generatePBlockFromReport(treeUtilReport, tempFile.getAbsolutePath())) {
                if (alreadySeen.contains(s)) continue;
                alreadySeen.add(s);
                for (int j = i - TREE_NUMBER / GROUP_NUMBER + 1; j <= i; j++) {
                    Tree o = trees.get(j);
                    o.setCoordinates(s);
                    o.setTreeNumber(0); //Assign tree number to each tree in bank
                    o.setBankNumber(i / (TREE_NUMBER/BANKS));
                    o.setGroup(j / (TREE_NUMBER/GROUP_NUMBER) % GROUP_NUMBER + 1); //Assign group number to each tree
                }

                requested--;
                if(requested == 0) break;
            }
            pw.write("create_pblock " + t.gettName() + "\n" +
                    "add_cells_to_pblock [get_pblocks " + t.gettName() + " ] [get_cells -quiet [list top_design_i/tree_rp_" + t.getBankNumber() + "_" + t.getTreeNumber() +"]]\n" +
                    "resize_pblock [get_pblocks " + t.gettName() +"] -add {" + t.getCoordinates() + "}\n" +
                    "set_property SNAPPING_MODE ON [get_pblocks " + t.gettName() + "]\n");
        }
        pw.close();

        for (Tree t : trees){
            System.out.println(t);
        }

        System.out.println("\n------------------------------------------------------------------------------\n");
        createCSV(trees, csvFile.getAbsolutePath());

    }

    //FIXME: non vengono evitati i pblocks anche se inseriti in global.txt manualmente
}


