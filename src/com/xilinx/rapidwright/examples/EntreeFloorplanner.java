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
    static final String CREATE_DCP = "dcpScript";
    static final String VIVADO_PATH = "/home/locav/Xilinx/Vivado/2021.2/bin/vivado";
    static final String DIR = "/home/locav/dcp/";

    private static void createTclScript(String scriptName, String dcpFileName, PBlock pblock){
        String pblockName = "pblock_1";
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(scriptName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("ERROR: Couldn't create Tcl script " + scriptName);
        }
        if(FileTools.isWindows()){
            dcpFileName = dcpFileName.replace("\\", "/");
        }
        pw.println("open_checkpoint " + dcpFileName);
        pw.println("create_pblock " + pblockName);
        pw.println("resize_pblock "+pblockName+" -add {"+pblock.toString()+"}");
        pw.println("add_cells_to_pblock "+pblockName+" -top");
        pw.println("set_property CONTAIN_ROUTING 1 [get_pblocks "+ pblockName+"]");
        pw.println("place_design");
        pw.println("write_checkpoint -force " + DIR + "placed_pblocks.dcp");
        pw.close();
    }
    public static Job createDCP(String dcpFileName, PBlock pblock, String path){
        String currDir = DIR;
        Job j = JobQueue.createJob();

        j.setRunDir(path);
        j.setCommand(VIVADO_PATH + " -mode batch -source " + "/home/locav/necst/RapidWright/dcpScript");
        FileTools.makeDirs(currDir);
        createTclScript(CREATE_DCP, dcpFileName, pblock);

        return j;
    }
    public static void main(String[] args) {
        JobQueue jobs = new JobQueue();
        Map<Long,String> jobLocations = new HashMap<>();

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

        Map<Tree, String> treeMap = new HashMap<>();
        Design d = new Design();

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
                t.sliceCount = (yb - ya + 1) * (xb - xa + 1);
                treeMap.put(t, s); //map trees to their temporary coordinates
                treeMap.forEach((K,V) -> System.out.println(K + "\t" + V));

                if(alreadySeen.contains(s)) continue;
                System.out.println(s + "\t" + t.sliceCount);
                alreadySeen.add(s);

                PBlock pblock = new PBlock(Device.getDevice(DEVICE), s);
                Job j = JobQueue.createJob();
                Job job = createDCP(t.getUtilReport().replace("_utilization_synth.rpt", ".dcp"), pblock, t.getUtilReport().substring(0, 59));//TODO aggiusta argomenti
                jobs.addJob(job);
                jobLocations.put(job.getJobNumber(), t.getDesign().toString());

                requested--;
                if(requested == 0) break;
            }
        }
        jobs.runAllToCompletion();
        //TODO: Command failed: Placer could not place all instances

        //Sort Tree's list by number of slices
        trees.sort(Comparator.comparing(a -> a.sliceCount));
        trees.forEach(System.out::println);




        for (int i = TREE_NUMBER/GROUP_NUMBER - 1; i  <= TREE_NUMBER - 1; i += TREE_NUMBER/GROUP_NUMBER){
            System.out.println(trees.get(i));
            //TODO: group trees

        }


    }

}


