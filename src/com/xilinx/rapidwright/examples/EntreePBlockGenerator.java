package com.xilinx.rapidwright.examples;

public class EntreePBlockGenerator {
    //TODO: pblockgenerator args
    private String UTILIZATION_REPORT_OPT;
    private String SHAPES_REPORT_OPT;
    private String ASPECT_RATIO_OPT;
    private String OVERHEAD_RATIO_OPT;
    private String STARTING_X_OPT;
    private String STARTING_Y_OPT;
    private String COUNT_REQUEST_OPT;
    private String GLOBAL_PBLOCK_OPT;
    private String IP_NR_INSTANCES_OPT;

    private EntreePBlockGenerator() {};

    public static class Builder{
        private final String UTILIZATION_REPORT_OPT;
        private String SHAPES_REPORT_OPT;
        private String ASPECT_RATIO_OPT;
        private String OVERHEAD_RATIO_OPT;
        private String STARTING_X_OPT;
        private String STARTING_Y_OPT;
        private String COUNT_REQUEST_OPT;
        private String GLOBAL_PBLOCK_OPT;
        private String IP_NR_INSTANCES_OPT;

        public String getSHAPES_REPORT_OPT() {
            return SHAPES_REPORT_OPT;
        }
        public EntreePBlockGenerator build(){
            EntreePBlockGenerator entreePBlockGenerator = new EntreePBlockGenerator();
            entreePBlockGenerator.UTILIZATION_REPORT_OPT = this.UTILIZATION_REPORT_OPT;
            entreePBlockGenerator.ASPECT_RATIO_OPT = this.ASPECT_RATIO_OPT;
            entreePBlockGenerator.SHAPES_REPORT_OPT = this.SHAPES_REPORT_OPT;
            entreePBlockGenerator.COUNT_REQUEST_OPT = this.COUNT_REQUEST_OPT;
            entreePBlockGenerator.OVERHEAD_RATIO_OPT = this.OVERHEAD_RATIO_OPT;
            return entreePBlockGenerator;
        }
        public Builder setSHAPES_REPORT_OPT(String SHAPES_REPORT_OPT) {
            this.SHAPES_REPORT_OPT = SHAPES_REPORT_OPT;
            return this;
        }

        public String getASPECT_RATIO_OPT() {
            return ASPECT_RATIO_OPT;
        }

        public Builder setASPECT_RATIO_OPT(String ASPECT_RATIO_OPT) {
            this.ASPECT_RATIO_OPT = ASPECT_RATIO_OPT;
            return this;
        }

        public String getOVERHEAD_RATIO_OPT() {
            return OVERHEAD_RATIO_OPT;
        }

        public Builder setOVERHEAD_RATIO_OPT(String OVERHEAD_RATIO_OPT) {
            this.OVERHEAD_RATIO_OPT = OVERHEAD_RATIO_OPT;
            return this;
        }

        public String getSTARTING_X_OPT() {
            return STARTING_X_OPT;
        }

        public Builder setSTARTING_X_OPT(String STARTING_X_OPT) {
            this.STARTING_X_OPT = STARTING_X_OPT;
            return this;
        }

        public String getSTARTING_Y_OPT() {
            return STARTING_Y_OPT;
        }

        public Builder setSTARTING_Y_OPT(String STARTING_Y_OPT) {
            this.STARTING_Y_OPT = STARTING_Y_OPT;
            return this;
        }

        public String getCOUNT_REQUEST_OPT() {
            return COUNT_REQUEST_OPT;
        }

        public Builder setCOUNT_REQUEST_OPT(String COUNT_REQUEST_OPT) {
            this.COUNT_REQUEST_OPT = COUNT_REQUEST_OPT;
            return this;
        }

        public String getGLOBAL_PBLOCK_OPT() {
            return GLOBAL_PBLOCK_OPT;
        }

        public Builder setGLOBAL_PBLOCK_OPT(String GLOBAL_PBLOCK_OPT) {
            this.GLOBAL_PBLOCK_OPT = GLOBAL_PBLOCK_OPT;
            return this;
        }

        public String getIP_NR_INSTANCES_OPT() {
            return IP_NR_INSTANCES_OPT;
        }

        public Builder setIP_NR_INSTANCES_OPT(String IP_NR_INSTANCES_OPT) {
            this.IP_NR_INSTANCES_OPT = IP_NR_INSTANCES_OPT;
            return this;
        }

        public Builder(String UTILIZATION_REPORT_OPT){
            //Default
            this.UTILIZATION_REPORT_OPT = UTILIZATION_REPORT_OPT;
            this.SHAPES_REPORT_OPT = "";
            this.ASPECT_RATIO_OPT = "0.016";
            this.OVERHEAD_RATIO_OPT = "1.0"; //no overhead
            this.STARTING_X_OPT = "";
            this.STARTING_Y_OPT = "";
            this.COUNT_REQUEST_OPT = "1";
            this.GLOBAL_PBLOCK_OPT = "";
            this.IP_NR_INSTANCES_OPT = "";
        }

    }

}
