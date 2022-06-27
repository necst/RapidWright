package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import it.necst.entree.Tree;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    static final int ROW_SIZE = 60;
    static final int GROUP_NUMBER = 3; //Apriori defined number of groups
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";

    public static void XDCwriter(List<Tree> trees) throws IOException {
        FileWriter fw = new FileWriter("/home/locav/const.xdc", true);
        BufferedWriter bw = new BufferedWriter(fw);
        for (Tree t : trees){
            String treeName = t.gettName();
            //TODO: you don't need to use all pblocks, just the first of each group
            bw.write("create_pblock \n" + treeName +
                        "add_cells_to_pblock [get_pblocks " + treeName + " ] [get_cells -quiet [list top_design_i/tree_rp_1_2]]\n" +
                        "resize_pblock [get_pblocks " + treeName +"] -add {" + t.coordinates + "}\n" +
                        "set_property SNAPPING_MODE ON [get_pblocks " + treeName + "]");
            bw.newLine();
        }
        bw.close();
    }
    public static void main(String[] args) {

        int TREE_NUMBER = args.length;

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(path.replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        File tempFile = null;
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

            HashSet<String> alreadySeen = new HashSet<String>();
            int requested = pbGen.PBLOCK_COUNT;
            for(String s : pbGen.generatePBlockFromReport(t.getUtilReport(), tempFile.getAbsolutePath())){

                //Get number of required slices for each tree
                Matcher matcher = pblockCoordinates.matcher(s);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("Regex failed!");
                }
                xa = Integer.parseInt(matcher.group("xa"));
                ya = Integer.parseInt(matcher.group("ya"));
                xb = Integer.parseInt(matcher.group("xb"));
                yb = Integer.parseInt(matcher.group("yb"));
                t.sliceCount = (yb - ya + 1) * (xb - xa + 1);

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

        //LOOP #2 to generate the final Pblocks
        for (int i = TREE_NUMBER/GROUP_NUMBER - 1; i  <= TREE_NUMBER - 1; i += TREE_NUMBER/GROUP_NUMBER) {
            Tree t = trees.get(i);
            String treeUtilReport = t.getUtilReport();

            //System.out.println(t);
            PBlockGenerator p = new PBlockGenerator.Builder()
                    .setGLOBAL_PBLOCK("/home/locav/global.txt")
                    .setIP_NR_INSTANCES(1)
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(1.2F) //with overhead = 1 it fails placing | 70% CLB utilization with 1.2
                    .build();

            HashSet<String> alreadySeen = new HashSet<String>();
            int requested = p.PBLOCK_COUNT;
            for (String s : p.generatePBlockFromReport(treeUtilReport, tempFile.getAbsolutePath())) {
                //Get number of required slices for each tree
                Matcher matcher = pblockCoordinates.matcher(s);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("Regex failed!");
                }
                xa = Integer.parseInt(matcher.group("xa"));
                ya = Integer.parseInt(matcher.group("ya"));
                xb = Integer.parseInt(matcher.group("xb"));
                yb = Integer.parseInt(matcher.group("yb"));
                t.sliceCount = (yb - ya + 1) * (xb - xa + 1);

                if (alreadySeen.contains(s)) continue;
                //System.out.println(treeName + "\t" + s + "\t" + t.sliceCount);
                alreadySeen.add(s);

                for (int j = i - TREE_NUMBER / GROUP_NUMBER + 1; j <= i; j++) {
                    trees.get(j).setCoordinates(s);
                }

                requested--;
                if(requested == 0) break;
            }
        }
        for (Tree t : trees){
            System.out.println(t);
        }
        try {
            XDCwriter(trees);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}


