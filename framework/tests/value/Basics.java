import org.checkerframework.common.value.qual.*;

class Basics {

    public void boolTest() {
        boolean a = false;
        if (true) {
            a = true;
        }
        @BoolVal({true, false}) boolean b = a;

        //:: error: (assignment.type.incompatible)
        @BoolVal({false}) boolean c = a;
    }

    public void CharacterTest() {
        Character a = 'a';
        if (true) {
            a = 'b';
        }
        @IntVal({'a', 'b'}) Character b = a;

        //:: error: (assignment.type.incompatible)
        @IntVal({'a'}) Character c = a;
    }

    public void charTest() {
        char a = 'a';
        if (true) {
            a = 'b';
        }
        @IntVal({'a', 'b'}) char b = a;

        //:: error: (assignment.type.incompatible)
        @IntVal({'a'}) char c = a;
    }

    public void DoubleTest() {
        Double a = new Double(0.0);
        if (true) {
            a = 2.0;
        }
        @DoubleVal({0, 2}) Double b = a;

        //:: error: (assignment.type.incompatible)
        @DoubleVal({0}) Double c = a;
    }

    public void doubleTest() {
        double a = 0.0;
        if (true) {
            a = 2.0;
        }
        @DoubleVal({0, 2}) double b = a;

        //:: error: (assignment.type.incompatible)
        @DoubleVal({0}) double c = a;
    }

    public void FloatTest() {
        Float a = new Float(0.0f);
        if (true) {
            a = 2.0f;
        }
        @DoubleVal({0, 2}) Float b = a;

        //:: error: (assignment.type.incompatible)
        @DoubleVal({0}) Float c = a;
    }

    public void floatTest() {
        float a = 0.0f;
        if (true) {
            a = 2.0f;
        }
        @DoubleVal({0, 2}) float b = a;

        //:: error: (assignment.type.incompatible)
        @DoubleVal({'a'}) float c = a;
    }

    public void IntegerTest(
            @IntRange(from = 3, to = 4) Integer x, @IntRange(from = 20, to = 30) Integer y) {
        Integer a;

        /* IntVal + IntVal */
        a = new Integer(0);
        if (true) {
            a = 2;
        }
        //:: error: (assignment.type.incompatible)
        @IntVal({0}) Integer test1 = a;
        @IntVal({0, 2}) Integer test2 = a;

        /* IntRange + IntRange */
        a = x;
        @IntVal({3, 4}) Integer test3 = a;
        if (true) {
            a = y;
        }
        @IntRange(from = 15, to = 30)
        //:: error: (assignment.type.incompatible)
        Integer test4 = a;
        @IntRange(from = 3, to = 30)
        Integer test5 = a;

        /* IntRange + IntVal */
        a = new Integer(0);
        if (true) {
            a = x;
        }
        @IntVal({0, 3, 4}) Integer test6 = a;
        @IntRange(from = 0, to = 4)
        Integer test7 = a;

        a = new Integer(0);
        if (true) {
            a = y;
        }
        @IntRange(from = 1, to = 30)
        //:: error: (assignment.type.incompatible)
        Integer test8 = a;
        @IntRange(from = 0, to = 30)
        Integer test9 = a;
    }

    public void intTest(@IntRange(from = 3, to = 4) int x, @IntRange(from = 20, to = 30) int y) {
        int a;

        /* IntVal + IntVal */
        a = 0;
        if (true) {
            a = 2;
        }
        //:: error: (assignment.type.incompatible)
        @IntVal({0}) int test1 = a;
        @IntVal({0, 2}) int test2 = a;

        /* IntRange + IntRange */
        a = x;
        @IntVal({3, 4}) int test3 = a;
        if (true) {
            a = y;
        }
        @IntRange(from = 15, to = 30)
        //:: error: (assignment.type.incompatible)
        int test4 = a;
        @IntRange(from = 3, to = 30)
        int test5 = a;

        /* IntRange + IntVal */
        a = 0;
        if (true) {
            a = x;
        }
        @IntVal({0, 3, 4}) int test6 = a;
        @IntRange(from = 0, to = 4)
        int test7 = a;

        a = 0;
        if (true) {
            a = y;
        }
        @IntRange(from = 1, to = 30)
        //:: error: (assignment.type.incompatible)
        int test8 = a;
        @IntRange(from = 0, to = 30)
        int test9 = a;
    }

    public void IntDoubleTest(
            @IntVal({0, 1}) int iv,
            @IntRange(from = 2, to = 3) int ir,
            @IntRange(from = 2, to = 20) int irw,
            @DoubleVal({4.0, 5.0}) double dv1,
            @DoubleVal({4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0}) double dv2) {
        double a;

        /* IntVal + DoubleVal */
        a = iv;
        if (true) {
            a = dv1;
        }
        //:: error: (assignment.type.incompatible)
        @DoubleVal({4.0, 5.0}) double test1 = a;
        @DoubleVal({0.0, 1.0, 2.0, 3.0, 4.0, 5.0}) double test2 = a;

        /* IntRange + DoubleVal */
        a = ir;
        //:: error: (assignment.type.incompatible)
        @DoubleVal({2.0}) double test3 = a;
        @DoubleVal({2.0, 3.0}) double test4 = a;
        if (true) {
            a = dv1;
        }
        //:: error: (assignment.type.incompatible)
        test1 = a;
        test2 = a;

        a = irw;
        if (true) {
            a = dv2;
        }
        //:: error: (assignment.type.incompatible)
        @DoubleVal({4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0}) double test5 = a;
        @UnknownVal double test6 = a;
    }

    public void stringTest() {
        String a = "test1";
        if (true) {
            a = "test2";
        }
        @StringVal({"test1", "test2"}) String b = a;

        //:: error: (assignment.type.incompatible)
        @StringVal({"test1"}) String c = a;
    }

    public void stringCastTest() {
        Object a = "test1";
        @StringVal({"test1"}) String b = (String) a;
        @StringVal({"test1"}) String c = (java.lang.String) b;
    }

    void tooManyValuesInt() {
        //:: warning: (too.many.values.given.int)
        @IntVal({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 100}) int a = 8;

        @UnknownVal int b = a; // This should always succeed

        @UnknownVal int c = 20;

        a = c; // This should succeed if a is treated as @IntRange(from=1, to=100)

        //:: warning: (too.many.values.given.int)
        @IntVal({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
        //:: error: (assignment.type.incompatible)
        int d = a; // d is @IntRange(from=1, to=12), a is @IntVal({20});
    }

    //    void fromGreaterThanTo() {
    //        //:: error: (from.greater.than.to)
    //        @IntRange(from = 2, to = 0)
    //        int a;
    //
    //        a = 3; //should succeed since a is treated as @UnknownVal
    //    }
    //
    void tooManyValuesDouble() {
        //:: warning: (too.many.values.given)
        @DoubleVal({1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0}) double a = 8.0;

        @UnknownVal double b = a; // This should always succeed

        @UnknownVal double c = 0;

        a = c; // This should succeed if a is treated as @UnknownVal

        //:: warning: (too.many.values.given)
        @DoubleVal({1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0}) double d = 8.0;

        d = 2.0 * d; // This should succeed since d is @UnknownVal
    }

    void tooManyValuesString() {
        //:: warning: (too.many.values.given)
        @StringVal({"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"}) String a = "h";

        @UnknownVal String b = a; // This should always succeed

        @UnknownVal String c = "";

        a = c; // This should succeed if a is treated as @UnknownVal

        //:: warning: (too.many.values.given)
        @StringVal({"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"}) String d = "h";

        d = "b" + d; // This should succeed since d is @UnknownVal
    }
}
