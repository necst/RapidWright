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
        //Design design = Design.readCheckpoint(args[0], true);
        //Tree tree = new Tree(design);
        //System.out.println(tree);

        List<Tree> trees =
                Arrays.stream(args)
                        .map(path -> new Tree(path.replace("dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        EntreePBlockGenerator pblock = new EntreePBlockGenerator.Builder()
                .setINPUT_LIST(trees)
                .build();
        
    }
}
