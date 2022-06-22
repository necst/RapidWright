package it.necst.entree;

import com.xilinx.rapidwright.design.Design;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Tree {
    public static final Pattern treeName = Pattern.compile("^tree_rm_\\d+_\\d+_inst_\\d+_tree_cl(?<class>\\d+)_(?<estimator>\\d+)_");
    public final String utilReport;
    public final int estimatorId;
    public final int classId;
    public final Design design;
    public String coordinates;

    public int sliceCount;
    public Tree(String utilReport, Design design) {
        this.utilReport = utilReport;
        this.design = design;
        this.coordinates = getCoordinates();

        String name = design.getNetlist().getTopCell().getCellInst("inst").getCellName();
        Matcher matcher = treeName.matcher(name);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Supplied DCP does not contain tree");
        }
        this.estimatorId = Integer.parseInt(matcher.group("estimator"));
        this.classId = Integer.parseInt(matcher.group("class"));
    }

    @Override
    public String toString() {
        return "Tree{" +
                "estimatorId=" + estimatorId +
                ", classId=" + classId +
                ", sliceCount=" + sliceCount +
                ", coordinates=" + coordinates +
                '}';
    }

    public int getClassId(){
        return this.classId;
    }
    public int getEstimatorId(){
        return this.estimatorId;
    }
    public int getSliceCount() {
        return sliceCount;
    }
    public String getUtilReport() {
        return utilReport;
    }
    public Design getDesign() {
        return design;
    }
    public String getCoordinates() {
        return coordinates;
    }
    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tree tree = (Tree) o;
        return estimatorId == tree.estimatorId && classId == tree.classId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(estimatorId, classId);
    }
}
