package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import it.necst.entree.Tree;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class EntreeFloorplanner {

    static final int OVERHEAD_RATIO = 1;
    static final int COL_SIZE = 1;
    static final int ROW_SIZE = 60;
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";

    public static void main(String[] args) {
        //TODO: handle command line args
        //Design design = Design.readCheckpoint(args[0], true);
        //Tree tree = new Tree(design);
        //System.out.println(tree);

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(path.replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        //TODO: Support shapes report

        File tempFile = null;
        try {
            tempFile = File.createTempFile(SHAPES_REPORT_FILE_NAME, ".txt");
        } catch (IOException e) {
            System.out.println("Unable to create tmp file.");
            throw new RuntimeException(e);
        }

        tempFile.deleteOnExit();


        for (Tree t : trees) {
            PBlockGenerator pbGen = new PBlockGenerator.Builder()
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .build();
            HashSet<String> alreadySeen = new HashSet<String>();
            int requested = pbGen.PBLOCK_COUNT;
            for(String s : pbGen.generatePBlockFromReport(t.getUtilReport(), tempFile.getAbsolutePath())){
                if(alreadySeen.contains(s)) continue;
                System.out.println(s);
                alreadySeen.add(s);
                requested--;
                if(requested == 0) break;
            }
        }
    }
}

