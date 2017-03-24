// @skip-test

import java.util.Comparator;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TreeSetTest {

    public static void main(String[] args) {

        TreeSet<@Nullable Integer> tsni =
                new TreeSet<@Nullable Integer>(new IntegerOrNullComparator());
        TreeSet<Integer> tsi = new TreeSet<Integer>();
        //:: error: (type.argument.type.incompatible)
        TreeSet<@Nullable Integer> tsniBad = new TreeSet<@Nullable Integer>();

        tsni.add(5); // OK
        tsni.add(null); // OK
        tsi.add(5); // OK
        //:: error: (argument.type.incompatible)
        tsi.add(null);
    }

    private static class IntegerOrNullComparator implements Comparator<@Nullable Integer> {
        public int compare(@Nullable Integer o1, @Nullable Integer o2) {
            // Treat null as 0
            if (o1 == null) {
                o1 = 0;
            }
            if (o2 == null) {
                o2 = 0;
            }
            return o1.compareTo(o2);
        }
    }

    private static class IntegerComparator implements Comparator<Integer> {
        public int compare(Integer o1, Integer o2) {
            return o1.compareTo(o2);
        }
    }
}
