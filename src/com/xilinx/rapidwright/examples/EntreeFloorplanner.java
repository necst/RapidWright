package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import it.necst.entree.EntreePBlockGenerator;
import it.necst.entree.Tree;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EntreeFloorplanner {

    public static void main(String[] args) {
        //TODO: handle command line args
        Design design = Design.readCheckpoint(args[0], true);
        Tree tree = new Tree(design);
        System.out.println(tree);

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        //TODO: incorporate PBlockGenerator information
    }

    EntreePBlockGenerator pblock = new EntreePBlockGenerator.Builder("path")
            .setGLOBAL_PBLOCK_OPT("aa")
            .setASPECT_RATIO_OPT("1")
            .setCOUNT_REQUEST_OPT("1")
            .setOVERHEAD_RATIO_OPT("1")
            .setSHAPES_REPORT_OPT("path")
            .setIP_NR_INSTANCES_OPT("1")
            .setSTARTING_X_OPT("0")
            .setSTARTING_Y_OPT("0").build();
}
