package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.Job;
import com.xilinx.rapidwright.util.JobQueue;
import it.necst.entree.Tree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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
    public static final String DEVICE = "xczu3eg-sbva484-1-i";
    static final int OVERHEAD_RATIO = 1;
    static final int COL_SIZE = 1;
    static final int ROW_SIZE = 60;
    static final int GROUP_NUMBER = 3; //Apriori defined number of groups
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";
    static final String VIVADO_PATH = "/home/locav/Xilinx/Vivado/2021.2/bin/vivado";
    static final String WORKING_DIR = "/home/locav/dcp/";//TODO: tmp director
    private static void createTclScript(String scriptName, String dcpFileName, PBlock pblock){
        String pblockName = dcpFileName.substring(102,135);
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(WORKING_DIR + scriptName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("ERROR: Couldn't create Tcl script " + scriptName);
        }
        pw.println("open_checkpoint " + dcpFileName);
        pw.println("create_pblock " + pblockName);
        pw.println("resize_pblock "+pblockName+" -add {"+pblock.toString()+"}");
        pw.println("write_xdc -force " + WORKING_DIR + pblockName + ".xdc"); //mi basta creare un file xdc per ogni albero
        pw.close();
    }
    private static void linkDesignTclScript(String scriptName, String dcpFileName_1, String xdcFileName){
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(WORKING_DIR + scriptName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("ERROR: Couldn't create Tcl script " + scriptName);
        }
        pw.println("open_checkpoint " + dcpFileName_1);
        pw.println("read_xdc -no_add " + WORKING_DIR + xdcFileName + " -quiet_diff_pairs");
        pw.println("write_checkpoint -force " + WORKING_DIR + dcpFileName_1);
        pw.close();
    }
    public static Job createDCP(String dcpFileName, String s, String path){
        String tclFileName = "run.tcl";
        PBlock pblock = new PBlock(Device.getDevice(DEVICE), s);

        Job j = JobQueue.createJob();
        j.setRunDir(path);
        j.setCommand(VIVADO_PATH + " -mode batch -source " + WORKING_DIR + tclFileName);
        FileTools.makeDirs(WORKING_DIR);
        createTclScript(tclFileName, dcpFileName, pblock);

        return j;
    }
    public static Job createUnifiedDesign(String dcpFileName_1, String xdcFileName, String path){
        String tclFileName = "link.tcl";

        Job j = JobQueue.createJob();
        j.setRunDir(path);
        j.setCommand(VIVADO_PATH + " -mode batch -source " + WORKING_DIR + tclFileName);
        FileTools.makeDirs(WORKING_DIR);
        linkDesignTclScript(tclFileName, dcpFileName_1, xdcFileName);

        return j;
    }
    public static void main(String[] args) {
        JobQueue jobs = new JobQueue();

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
            PBlockGenerator pbGen = new PBlockGenerator.Builder()
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(1.2F) //with overhead = 1 it fails placing
                    .setGLOBAL_PBLOCK("/home/locav/dcp/empty.dcp")//use empty dcp
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
                System.out.println(t.utilReport.substring(102, 135) + "\t" + s + "\t" + t.sliceCount); //print current state of tree TODO: use toString
                alreadySeen.add(s);
                requested--;
                if(requested == 0) break;
            }
        }

        //Sort Tree's list by number of slices
        trees.sort(Comparator.comparing(a -> a.sliceCount));
        //trees.forEach(System.out::println);

        //LOOP #2 to generate the final Pblocks
        for (int i = TREE_NUMBER/GROUP_NUMBER - 1; i  <= TREE_NUMBER - 1; i += TREE_NUMBER/GROUP_NUMBER){
            Tree t = trees.get(i);
            String treeName = t.utilReport.substring(102, 135);
            String treeUtilReport = t.getUtilReport();

            System.out.println(t);
            PBlockGenerator p = new PBlockGenerator.Builder()
                    .setGLOBAL_PBLOCK("/home/locav/dcp/tree_rm_0_2_inst_5_tree_cl0_2_0_0.xdc") //why it doesn't avoid the pblock??!!
                    .setOVERHEAD_RATIO(OVERHEAD_RATIO)
                    .setASPECT_RATIO((float) COL_SIZE / (float) ROW_SIZE)
                    .setOVERHEAD_RATIO(1.2F) //with overhead = 1 it fails placing | 70% CLB utilization with 1.2
                    .build();

//                  ┌──────────────────────────────────────────────────────────────────┐
//                  │                                                                  │
//                  │  If you want to instruct the algorithm to avoid certain columns, │
//                  │                                                                  │
//                  │  you can insert in the dcp file some pblocks.                    │
//                  │                                                                  │
//                  └──────────────────────────────────────────────────────────────────┘

            HashSet<String> alreadySeen = new HashSet<String>();
            int requested = p.PBLOCK_COUNT;
            for(String s : p.generatePBlockFromReport(treeUtilReport, tempFile.getAbsolutePath())){
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
                System.out.println(treeName + "\t" + s + "\t" + t.sliceCount);//current state of trees
                alreadySeen.add(s);

                Job j = JobQueue.createJob();
                Job job = createDCP(treeUtilReport.replace("_utilization_synth.rpt", ".dcp"), s, treeUtilReport.substring(0, 59));//TODO: aggiusta gli argomenti
                jobs.addJob(job);

                requested--;
                if(requested == 0) break;
            }

            //TODO: link checkpoint: checkpoint_1 = checkpoint_1 + treeName.xdc
            Job j = createUnifiedDesign("checkpoint_1.dcp", treeName + ".xdc", WORKING_DIR);
            jobs.addJob(j);
            break;
        }

        jobs.runAllToCompletion();//TODO: attenzione all'ordine di esecuzione degli script! non usare JobQueue ma usa job.launchJob()
    }
}


