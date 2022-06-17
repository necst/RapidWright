package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
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
                        .map(path -> new Tree(path.replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(path, true)))
                        .collect(Collectors.toList());

        PBlockGenerator pbgen  = new PBlockGenerator.Builder()
                .setINPUT_LIST(trees)
                //.setUTILIZATION_REPORT("/home/locav/entree/entree_dfx_design/entree_dfx_design.runs/tree_rm_0_2_inst_5_tree_cl0_2_0_0_synth_1/tree_rm_0_2_inst_5_tree_cl0_2_0_0_utilization_synth.rpt")
                .setOVERHEAD_RATIO("1")
                .setASPECT_RATIO("0.016")
                .build();
    }

}
