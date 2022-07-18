package com.xilinx.rapidwright.examples;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlockGenerator;
import it.necst.entree.Tree;

import java.io.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntreeFloorplanner {
    public static final Pattern pblockCoordinates = Pattern.compile("^SLICE_X(?<xa>\\d+)Y(?<ya>\\d+):SLICE_X(?<xb>\\d+)Y(?<yb>\\d+)$");
    public static final Pattern dirName = Pattern.compile("^tree_rm_\\d+_\\d+_inst_\\d+_tree_cl\\d+_\\d+_\\d+_\\d+_synth_\\d+$");
    static final float OVERHEAD_RATIO = 1.2F;               //with overhead = 1 it fails placing | 70% CLB utilization with overhead = 1.2
    static final int COL_SIZE = 1;
    static final int ROW_SIZE = 60;                         //row and col size defined by the FPGA's fabric
    static final String SHAPES_REPORT_FILE_NAME = "shape.txt";
    static final String GLOBAL_PBLOCKS_FILE_NAME = "global_pblocks.txt";
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
    public static void main(String[] args) throws IOException {

        int RECONFIGURABLE_REGIONS = Integer.parseInt(args[0]);
        int BANKS = Integer.parseInt(args[1]);
        File RUNS_DIR = new File(args[2]);
        File CONSTRAINTS_FILE_NAME = new File(args[3]);
        File CSV_FILE_NAME = new File(args[4]);

        TreeMap<String, String> reconfigurableRegions = new TreeMap<>();

        File globalPblocksFile = new File(GLOBAL_PBLOCKS_FILE_NAME);
        File constraintsFile = new File(CONSTRAINTS_FILE_NAME.getAbsolutePath());
        File csvFile = new File(CSV_FILE_NAME.getAbsolutePath());
        globalPblocksFile.createNewFile();
        constraintsFile.createNewFile();
        csvFile.createNewFile();

        File tempFile;
        try {
            tempFile = File.createTempFile(SHAPES_REPORT_FILE_NAME, "");

        } catch (IOException e) {
            System.out.println("Unable to create tmp file.");
            throw new RuntimeException(e);
        }
        tempFile.deleteOnExit();

        List<Tree> trees = new ArrayList<>();

        if (RUNS_DIR.exists() && RUNS_DIR.isDirectory()) {
            for (File child : Objects.requireNonNull(RUNS_DIR.listFiles())) {
                if (child.getName().matches(String.valueOf(dirName))) {
                    for (File dcp : Objects.requireNonNull(child.listFiles())) {
                        if (dcp.getName().endsWith(".dcp")) {
                            Tree t = new Tree(dcp.getAbsolutePath().replace(".dcp", "_utilization_synth.rpt"), Design.readCheckpoint(dcp.getAbsolutePath(), true));
                            trees.add(t);
                        }
                    }
                }
            }
        }
        int TREE_NUMBER = trees.size();

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
        PrintWriter pw = new PrintWriter(constraintsFile.getAbsolutePath());
        //LOOP #2 to generate the final Pblocks, csv and xdc files
        int m = 0, n = 0;
        for (int i = TREE_NUMBER/RECONFIGURABLE_REGIONS - 1; i  < TREE_NUMBER; i += TREE_NUMBER/RECONFIGURABLE_REGIONS) { //loop over groups
            if (n == RECONFIGURABLE_REGIONS/BANKS) {
                m++;
                n = 0;
            }
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

                String partition = "pblock_tree_rp_" + m + "_" + n;
                n++;


                reconfigurableRegions.put(partition, s);

                pw.write(   "create_pblock " + partition + "\n" +
                        "add_cells_to_pblock [get_pblocks " + partition + "] [get_cells -quiet [list top_design_i/" + partition + "]]\n" +
                        "resize_pblock [get_pblocks " + partition + "] -add {" + reconfigurableRegions.get(partition) + "}\n" +
                        "set_property SNAPPING_MODE ON [get_pblocks " + partition + "]\n");

                requested--;
                if(requested == 0) break;
            }
        }
        pw.close();

        for (Tree t : trees){
            System.out.println(t);
        }

        System.out.println("\n------------------------------------------------------------------------------\n");

        //CSV writer

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        df.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));

        PrintWriter csvWriter = new PrintWriter(csvFile.getAbsolutePath());
        StringBuilder csvBody = new StringBuilder();
        csvWriter.write("treeName,");
        for (int i = 0; i < RECONFIGURABLE_REGIONS; i++){
            if (i == RECONFIGURABLE_REGIONS - 1) {
                csvWriter.write(reconfigurableRegions.lastKey());
                break;
            }
            else csvWriter.write(reconfigurableRegions.keySet().toArray()[i] + ",");
        }
        csvWriter.write("\n");
        for (Tree t : trees) {
            csvBody.append(t.gettName()).append(",");
            for (String s : reconfigurableRegions.keySet()) {
                if (Objects.equals(s, reconfigurableRegions.lastKey())){
                    csvBody.append(df.format((float) t.getSliceCount() / (float) getSliceNumberFromCoordinates(reconfigurableRegions.get(s))));
                    break;
                }
                else csvBody.append(df.format((float) t.getSliceCount() / (float) getSliceNumberFromCoordinates(reconfigurableRegions.get(s)))).append(",");
            }
            csvBody.append("\n");
        }
        csvWriter.write(csvBody.toString());
        csvWriter.close();
    }
}


