package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import it.necst.entree.Tree;

import java.io.File;
import java.io.IOException;
import java.util.*;
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
    static final int NUMBER_OF_GROUPS = 3; //Defined number of groups
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";

    public static void main(String[] args) {

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

        Map<Integer, Integer> pblockMap = new HashMap<>();

        for (Tree t : trees) {
            PBlockGenerator pbGen = new PBlockGenerator.Builder()
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
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
                int sliceNumber = (yb - ya + 1) * (xb - xa + 1);

                if(alreadySeen.contains(s)) continue;
                System.out.println(s + "\t" + sliceNumber);
                alreadySeen.add(s);

                //TODO: store pbGen and sliceNumber on a data structure to sort and create groups

                requested--;
                if(requested == 0) break;
            }

        }

        //TODO: avoid overlapping
    }
}


