package org.checkerframework.common.value;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.value.qual.ArrayLen;
import org.checkerframework.common.value.qual.BoolVal;
import org.checkerframework.common.value.qual.BottomVal;
import org.checkerframework.common.value.qual.DoubleVal;
import org.checkerframework.common.value.qual.IntRange;
import org.checkerframework.common.value.qual.IntVal;
import org.checkerframework.common.value.qual.StaticallyExecutable;
import org.checkerframework.common.value.qual.StringVal;
import org.checkerframework.common.value.qual.UnknownVal;
import org.checkerframework.common.value.util.NumberUtils;
import org.checkerframework.common.value.util.Range;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ImplicitsTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionParseException;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

/**
 * AnnotatedTypeFactory for the Value type system.
 *
 * @author plvines
 * @author smillst
 */
public class ValueAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** The maximum number of values allowed in an annotation's array */
    protected static final int MAX_VALUES = 10;

    /**
     * The domain of the Constant Value Checker: the types for which it estimates possible values.
     */
    protected static final Set<String> coveredClassStrings;

    /** The top type for this hierarchy. */
    protected final AnnotationMirror UNKNOWNVAL;

    /** The bottom type for this hierarchy. */
    protected final AnnotationMirror BOTTOMVAL;

    /** Should this type factory report warnings? */
    private final boolean reportEvalWarnings;

    /** Helper class that evaluates statically executable methods, constructors, and fields. */
    private final ReflectiveEvalutator evalutator;

    static {
        Set<String> backingSet = new HashSet<String>(18);
        backingSet.add("int");
        backingSet.add("java.lang.Integer");
        backingSet.add("double");
        backingSet.add("java.lang.Double");
        backingSet.add("byte");
        backingSet.add("java.lang.Byte");
        backingSet.add("java.lang.String");
        backingSet.add("char");
        backingSet.add("java.lang.Character");
        backingSet.add("float");
        backingSet.add("java.lang.Float");
        backingSet.add("boolean");
        backingSet.add("java.lang.Boolean");
        backingSet.add("long");
        backingSet.add("java.lang.Long");
        backingSet.add("short");
        backingSet.add("java.lang.Short");
        backingSet.add("byte[]");
        coveredClassStrings = Collections.unmodifiableSet(backingSet);
    }

    public ValueAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);

        BOTTOMVAL = AnnotationUtils.fromClass(elements, BottomVal.class);
        UNKNOWNVAL = AnnotationUtils.fromClass(elements, UnknownVal.class);

        reportEvalWarnings = checker.hasOption(ValueChecker.REPORT_EVAL_WARNS);
        evalutator = new ReflectiveEvalutator(checker, this, reportEvalWarnings);

        if (this.getClass().equals(ValueAnnotatedTypeFactory.class)) {
            this.postInit();
        }
    }

    @Override
    public AnnotationMirror aliasedAnnotation(AnnotationMirror anno) {
        if (AnnotationUtils.areSameByClass(anno, android.support.annotation.IntRange.class)) {
            Range range = getIntRange(anno);
            return createIntRangeAnnotation(range);
        }
        return super.aliasedAnnotation(anno);
    }

    @Override
    public CFTransfer createFlowTransferFunction(
            CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return new ValueTransfer(analysis);
    }

    /**
     * Creates an annotation of the given name with the given set of values.
     *
     * <p>If values.size &gt; MAX_VALUES, issues a checker warning and returns UNKNOWNVAL.
     *
     * <p>If values.size == 0, issues a checker warning and returns BOTTOMVAL.
     *
     * @return annotation given by name with values=values, or UNKNOWNVAL
     */
    private AnnotationMirror createAnnotation(String name, Set<?> values) {
        if (values.size() == 0) {
            return BOTTOMVAL;
        }
        if (values.size() > MAX_VALUES) {
            return UNKNOWNVAL;
        }
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, name);
        List<Object> valuesList = new ArrayList<Object>(values);
        builder.setValue("value", valuesList);
        return builder.build();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return getBundledTypeQualifiersWithoutPolyAll();
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new ValueQualifierHierarchy(factory);
    }

    @Override
    protected TypeAnnotator createTypeAnnotator() {
        return new ListTypeAnnotator(new ValueTypeAnnotator(this), super.createTypeAnnotator());
    }

    /**
     * Creates array length annotations for the result of the Enum.values() method, which is the
     * number of possible values of the enum.
     */
    @Override
    public Pair<AnnotatedTypeMirror.AnnotatedExecutableType, List<AnnotatedTypeMirror>>
            methodFromUse(
                    ExpressionTree tree,
                    ExecutableElement methodElt,
                    AnnotatedTypeMirror receiverType) {

        Pair<AnnotatedTypeMirror.AnnotatedExecutableType, List<AnnotatedTypeMirror>> superPair =
                super.methodFromUse(tree, methodElt, receiverType);
        if (ElementUtils.matchesElement(methodElt, "values")
                && methodElt.getEnclosingElement().getKind() == ElementKind.ENUM
                && ElementUtils.isStatic(methodElt)) {
            int count = 0;
            List<? extends Element> l = methodElt.getEnclosingElement().getEnclosedElements();
            for (Element el : l) {
                if (el.getKind() == ElementKind.ENUM_CONSTANT) {
                    count++;
                }
            }
            AnnotationMirror am = createArrayLenAnnotation(Collections.singletonList(count));
            superPair.first.getReturnType().replaceAnnotation(am);
        }
        return superPair;
    }

    /**
     * Performs pre-processing on annotations written by users, replacing illegal annotations by
     * legal ones.
     */
    private class ValueTypeAnnotator extends TypeAnnotator {

        public ValueTypeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        @Override
        public Void visitPrimitive(AnnotatedPrimitiveType type, Void p) {
            replaceWithNewAnnoInSpecialCases(type);

            return super.visitPrimitive(type, p);
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType type, Void p) {
            replaceWithNewAnnoInSpecialCases(type);

            return super.visitDeclared(type, p);
        }

        /**
         * This method performs pre-processing on annotations written by users.
         *
         * <p>If any *Val annotation has &gt; MAX_VALUES number of values provided, replaces the
         * annotation by @IntRange for integral types and @UnknownVal for all other types. Works
         * together with {@link
         * org.checkerframework.common.value.ValueVisitor#visitAnnotation(com.sun.source.tree.AnnotationTree,
         * Void)} which issues warnings to users in these cases.
         *
         * <p>If any @IntRange annotation has incorrect parameters, e.g. the value "from" is
         * specified to be greater than the value "to", replaces the annotation by @BOTTOMVAL as
         * well. The {@link
         * org.checkerframework.common.value.ValueVisitor#visitAnnotation(com.sun.source.tree.AnnotationTree,
         * Void)} would raise error to users in this case.
         */
        private void replaceWithNewAnnoInSpecialCases(AnnotatedTypeMirror atm) {
            AnnotationMirror anno = atm.getAnnotationInHierarchy(UNKNOWNVAL);

            if (anno != null && anno.getElementValues().size() > 0) {
                if (AnnotationUtils.areSameByClass(anno, IntVal.class)) {
                    List<Long> values =
                            AnnotationUtils.getElementValueArray(anno, "value", Long.class, true);
                    if (values.size() > MAX_VALUES) {
                        long annoMinVal = Collections.min(values);
                        long annoMaxVal = Collections.max(values);
                        atm.replaceAnnotation(
                                createIntRangeAnnotation(new Range(annoMinVal, annoMaxVal)));
                    }
                } else if (AnnotationUtils.areSameByClass(anno, IntRange.class)) {
                    long from = AnnotationUtils.getElementValue(anno, "from", Long.class, true);
                    long to = AnnotationUtils.getElementValue(anno, "to", Long.class, true);
                    if (from > to) {
                        atm.replaceAnnotation(BOTTOMVAL);
                    }
                } else {
                    // In here the annotation is @*Val where (*) is not Int but other types (String, Double, etc).
                    // Therefore we extract its values in a generic way to check its size.
                    List<Object> values =
                            AnnotationUtils.getElementValueArray(
                                    anno, "value", Object.class, false);
                    if (values.size() > MAX_VALUES) {
                        atm.replaceAnnotation(UNKNOWNVAL);
                    }
                }
            }
        }
    }

    /** The qualifier hierarchy for the Value type system */
    private final class ValueQualifierHierarchy extends MultiGraphQualifierHierarchy {

        /** @param factory MultiGraphFactory to use to construct this */
        public ValueQualifierHierarchy(MultiGraphQualifierHierarchy.MultiGraphFactory factory) {
            super(factory);
        }

        @Override
        public AnnotationMirror greatestLowerBound(AnnotationMirror a1, AnnotationMirror a2) {
            if (isSubtype(a1, a2)) {
                return a1;
            } else if (isSubtype(a2, a1)) {
                return a2;
            } else {
                // If the two are unrelated, then bottom is the GLB.
                return BOTTOMVAL;
            }
        }

        /**
         * Determines the least upper bound of a1 and a2, which contains the union of their sets of
         * possible values.
         *
         * @return the least upper bound of a1 and a2
         */
        @Override
        public AnnotationMirror leastUpperBound(AnnotationMirror a1, AnnotationMirror a2) {
            if (!AnnotationUtils.areSameIgnoringValues(
                    getTopAnnotation(a1), getTopAnnotation(a2))) {
                // The annotations are in different hierarchies
                return null;
            }

            if (isSubtype(a1, a2)) {
                return a2;
            } else if (isSubtype(a2, a1)) {
                return a1;
            }

            if (AnnotationUtils.areSameIgnoringValues(a1, a2)) {
                // If both are the same type, determine the type and merge
                if (AnnotationUtils.areSameByClass(a1, IntRange.class)) {
                    // special handling for IntRange
                    Range range1 = getIntRange(a1);
                    Range range2 = getIntRange(a2);
                    return createIntRangeAnnotation(range1.union(range2));
                } else {
                    List<Object> a1Values =
                            AnnotationUtils.getElementValueArray(a1, "value", Object.class, true);
                    List<Object> a2Values =
                            AnnotationUtils.getElementValueArray(a2, "value", Object.class, true);
                    HashSet<Object> newValues =
                            new HashSet<Object>(a1Values.size() + a2Values.size());

                    newValues.addAll(a1Values);
                    newValues.addAll(a2Values);

                    // createAnnotation returns @UnknownVal if the list is longer than MAX_VALUES
                    return createAnnotation(a1.getAnnotationType().toString(), newValues);
                }
            }

            // Annotations are both in the same hierarchy, but they are not the same.
            // If a1 and a2 are not the same type of *Value annotation, they may still be mergeable
            // because some values can be implicitly cast as others. For example, if a1 and a2 are
            // both in {DoubleVal, IntVal} then they will be converted upwards: IntVal -> DoubleVal
            // to arrive at a common annotation type.

            // Each of these variables is an annotation of the given type, or is null if neither of
            // the arguments to leastUpperBound is of the given types.
            AnnotationMirror intValAnno = null;
            AnnotationMirror intRangeAnno = null;
            AnnotationMirror doubleValAnno = null;
            if (AnnotationUtils.areSameByClass(a1, IntVal.class)) {
                intValAnno = a1;
            } else if (AnnotationUtils.areSameByClass(a2, IntVal.class)) {
                intValAnno = a2;
            }
            if (AnnotationUtils.areSameByClass(a1, DoubleVal.class)) {
                doubleValAnno = a1;
            } else if (AnnotationUtils.areSameByClass(a2, DoubleVal.class)) {
                doubleValAnno = a2;
            }
            if (AnnotationUtils.areSameByClass(a1, IntRange.class)) {
                intRangeAnno = a1;
            } else if (AnnotationUtils.areSameByClass(a2, IntRange.class)) {
                intRangeAnno = a2;
            }

            if (doubleValAnno != null) {
                if (intRangeAnno != null) {
                    intValAnno = convertIntRangeToIntVal(intRangeAnno);
                    intRangeAnno = null;
                    if (intValAnno == UNKNOWNVAL) {
                        intValAnno = null;
                    }
                }
                if (intValAnno != null) {
                    // Convert intValAnno to a @DoubleVal AnnotationMirror
                    AnnotationMirror doubleValAnno2 = convertIntValToDoubleVal(intValAnno);
                    return leastUpperBound(doubleValAnno, doubleValAnno2);
                }
                return UNKNOWNVAL;
            }
            if (intRangeAnno != null && intValAnno != null) {
                // Convert intValAnno to an @IntRange AnnotationMirror
                AnnotationMirror intRangeAnno2 = convertIntValToIntRange(intValAnno);
                return leastUpperBound(intRangeAnno, intRangeAnno2);
            }

            // In all other cases, the LUB is UnknownVal.
            return UNKNOWNVAL;
        }

        /**
         * Computes subtyping as per the subtyping in the qualifier hierarchy structure unless both
         * annotations are Value. In this case, rhs is a subtype of lhs iff lhs contains at least
         * every element of rhs.
         *
         * @return true if rhs is a subtype of lhs, false otherwise
         */
        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {

            if (AnnotationUtils.areSameByClass(lhs, UnknownVal.class)
                    || AnnotationUtils.areSameByClass(rhs, BottomVal.class)) {
                return true;
            } else if (AnnotationUtils.areSameByClass(rhs, UnknownVal.class)
                    || AnnotationUtils.areSameByClass(lhs, BottomVal.class)) {
                return false;
            } else if (AnnotationUtils.areSameIgnoringValues(lhs, rhs)) {
                // Same type, so might be subtype
                if (AnnotationUtils.areSameByClass(rhs, IntRange.class)) {
                    // Special case for IntRange
                    Range lhsRange = getIntRange(lhs);
                    Range rhsRange = getIntRange(rhs);
                    return lhsRange.contains(rhsRange);
                } else {
                    List<Object> lhsValues =
                            AnnotationUtils.getElementValueArray(lhs, "value", Object.class, true);
                    List<Object> rhsValues =
                            AnnotationUtils.getElementValueArray(rhs, "value", Object.class, true);
                    return lhsValues.containsAll(rhsValues);
                }
            } else if (AnnotationUtils.areSameByClass(lhs, DoubleVal.class)
                    && AnnotationUtils.areSameByClass(rhs, IntVal.class)) {
                List<Double> rhsValues =
                        convertLongListToDoubleList(
                                AnnotationUtils.getElementValueArray(
                                        rhs, "value", Long.class, true));
                List<Double> lhsValues =
                        AnnotationUtils.getElementValueArray(lhs, "value", Double.class, true);
                return lhsValues.containsAll(rhsValues);
            } else if (AnnotationUtils.areSameByClass(lhs, IntRange.class)
                    && AnnotationUtils.areSameByClass(rhs, IntVal.class)) {
                List<Long> rhsValues =
                        AnnotationUtils.getElementValueArray(rhs, "value", Long.class, true);
                Range lhsRange = getIntRange(lhs);
                long rhsMinVal = Collections.min(rhsValues);
                long rhsMaxVal = Collections.max(rhsValues);
                return rhsMinVal >= lhsRange.from && rhsMaxVal <= lhsRange.to;
            } else if (AnnotationUtils.areSameByClass(lhs, DoubleVal.class)
                    && AnnotationUtils.areSameByClass(rhs, IntRange.class)) {
                Range rhsRange = getIntRange(rhs);
                if (rhsRange.isWiderThan(MAX_VALUES)) {
                    return false;
                }
                List<Double> lhsValues =
                        AnnotationUtils.getElementValueArray(lhs, "value", Double.class, true);
                List<Double> rhsValues =
                        ValueCheckerUtils.getValuesFromRange(rhsRange, Double.class);
                return lhsValues.containsAll(rhsValues);
            } else if (AnnotationUtils.areSameByClass(lhs, IntVal.class)
                    && AnnotationUtils.areSameByClass(rhs, IntRange.class)) {
                Range rhsRange = getIntRange(rhs);
                if (rhsRange.isWiderThan(MAX_VALUES)) {
                    return false;
                }
                List<Long> lhsValues =
                        AnnotationUtils.getElementValueArray(lhs, "value", Long.class, true);
                List<Long> rhsValues = ValueCheckerUtils.getValuesFromRange(rhsRange, Long.class);
                return lhsValues.containsAll(rhsValues);
            } else {
                return false;
            }
        }
    }

    @Override
    protected TreeAnnotator createTreeAnnotator() {
        // The ValueTreeAnnotator handles propagation differently,
        // so it doesn't need PropgationTreeAnnotator.
        return new ListTreeAnnotator(
                new ValueTreeAnnotator(this), new ImplicitsTreeAnnotator(this));
    }

    /** The TreeAnnotator for this AnnotatedTypeFactory. It adds/replaces annotations. */
    protected class ValueTreeAnnotator extends TreeAnnotator {

        public ValueTreeAnnotator(ValueAnnotatedTypeFactory factory) {
            super(factory);
        }

        @Override
        public Void visitNewArray(NewArrayTree tree, AnnotatedTypeMirror type) {

            List<? extends ExpressionTree> dimensions = tree.getDimensions();
            List<? extends ExpressionTree> initializers = tree.getInitializers();

            // Array construction can provide dimensions or use an initializer.

            // Dimensions provided
            if (!dimensions.isEmpty()) {
                handleDimensions(dimensions, (AnnotatedArrayType) type);
            } else {
                // Initializer used
                handleInitalizers(initializers, (AnnotatedArrayType) type);

                AnnotationMirror newQual;
                Class<?> clazz = ValueCheckerUtils.getClassFromType(type.getUnderlyingType());
                String stringVal = null;
                if (clazz.equals(byte[].class)) {
                    stringVal = getByteArrayStringVal(initializers);
                } else if (clazz.equals(char[].class)) {
                    stringVal = getCharArrayStringVal(initializers);
                }

                if (stringVal != null) {
                    newQual = createStringAnnotation(Collections.singletonList(stringVal));
                    type.replaceAnnotation(newQual);
                }
            }

            return null;
        }

        /**
         * Recursive method to handle array initializations. Recursively descends the initializer to
         * find each dimension's size and create the appropriate annotation for it.
         *
         * @param dimensions a list of ExpressionTrees where each ExpressionTree is a specifier of
         *     the size of that dimension (should be an IntVal)
         * @param type the AnnotatedTypeMirror of the array
         */
        private void handleDimensions(
                List<? extends ExpressionTree> dimensions, AnnotatedArrayType type) {
            if (dimensions.size() > 1) {
                handleDimensions(
                        dimensions.subList(1, dimensions.size()),
                        (AnnotatedArrayType) type.getComponentType());
            }

            AnnotationMirror dimType =
                    getAnnotatedType(dimensions.get(0)).getAnnotationInHierarchy(UNKNOWNVAL);
            if (!AnnotationUtils.areSameIgnoringValues(dimType, UNKNOWNVAL)) {
                List<Long> longLengths = getIntValues(dimType);

                HashSet<Integer> lengths = new HashSet<Integer>(longLengths.size());
                for (Long l : longLengths) {
                    lengths.add(l.intValue());
                }
                AnnotationMirror newQual = createArrayLenAnnotation(new ArrayList<>(lengths));
                type.replaceAnnotation(newQual);
            }
        }

        private void handleInitalizers(
                List<? extends ExpressionTree> initializers, AnnotatedArrayType type) {

            List<Integer> array = new ArrayList<>();
            array.add(initializers.size());
            type.replaceAnnotation(createArrayLenAnnotation(array));

            boolean singleDem = type.getComponentType().getKind() != TypeKind.ARRAY;
            if (singleDem) {
                return;
            }
            List<List<Integer>> summarylengths = new ArrayList<>();

            for (ExpressionTree init : initializers) {
                AnnotatedTypeMirror componentType = getAnnotatedType(init);
                int count = 0;
                while (componentType.getKind() == TypeKind.ARRAY) {
                    AnnotationMirror arrayLen = componentType.getAnnotation(ArrayLen.class);
                    List<Integer> currentLengths;
                    if (arrayLen != null) {
                        currentLengths = getArrayLength(arrayLen);
                    } else {
                        currentLengths = (new ArrayList<Integer>());
                    }
                    if (count == summarylengths.size()) {
                        summarylengths.add(new ArrayList<Integer>());
                    }
                    summarylengths.get(count).addAll(currentLengths);
                    count++;
                    componentType = ((AnnotatedArrayType) componentType).getComponentType();
                }
            }

            AnnotatedTypeMirror componentType = type.getComponentType();
            int i = 0;
            while (componentType.getKind() == TypeKind.ARRAY && i < summarylengths.size()) {
                componentType.addAnnotation(createArrayLenAnnotation(summarylengths.get(i)));
                componentType = ((AnnotatedArrayType) componentType).getComponentType();
                i++;
            }
        }

        /** Convert a byte array to a String. */
        private String getByteArrayStringVal(List<? extends ExpressionTree> initializers) {
            // True iff every element of the array is a literal.
            boolean allLiterals = true;
            byte[] bytes = new byte[initializers.size()];
            for (int i = 0; i < initializers.size(); i++) {
                ExpressionTree e = initializers.get(i);
                if (e.getKind() == Tree.Kind.INT_LITERAL) {
                    bytes[i] = (byte) (((Integer) ((LiteralTree) e).getValue()).intValue());
                } else if (e.getKind() == Tree.Kind.CHAR_LITERAL) {
                    bytes[i] = (byte) (((Character) ((LiteralTree) e).getValue()).charValue());
                } else {
                    allLiterals = false;
                }
            }
            if (allLiterals) {
                return new String(bytes);
            }
            // If any part of the initializer isn't known,
            // the stringval isn't known.
            return null;
        }

        /** Convert a char array to a String. */
        private String getCharArrayStringVal(List<? extends ExpressionTree> initializers) {
            boolean allLiterals = true;
            StringBuilder stringVal = new StringBuilder();
            for (ExpressionTree e : initializers) {
                if (e.getKind() == Tree.Kind.INT_LITERAL) {
                    char charVal = (char) (((Integer) ((LiteralTree) e).getValue()).intValue());
                    stringVal.append(charVal);
                } else if (e.getKind() == Tree.Kind.CHAR_LITERAL) {
                    char charVal = (((Character) ((LiteralTree) e).getValue()));
                    stringVal.append(charVal);
                } else {
                    allLiterals = false;
                }
            }
            if (allLiterals) {
                return stringVal.toString();
            }
            // If any part of the initializer isn't known,
            // the stringval isn't known.
            return null;
        }

        @Override
        public Void visitTypeCast(TypeCastTree tree, AnnotatedTypeMirror atm) {
            if (handledByValueChecker(atm)) {
                AnnotationMirror oldAnno =
                        getAnnotatedType(tree.getExpression()).getAnnotationInHierarchy(UNKNOWNVAL);
                if (oldAnno != null) {
                    TypeMirror newType = atm.getUnderlyingType();
                    AnnotationMirror newAnno;
                    if (AnnotationUtils.areSameByClass(oldAnno, IntRange.class)) {
                        Range range = getIntRange(oldAnno);
                        if (range.isWiderThan(MAX_VALUES)) {
                            Class<?> newClass = ValueCheckerUtils.getClassFromType(newType);
                            if (newClass == String.class) {
                                newAnno = UNKNOWNVAL;
                            } else if (newClass == Boolean.class || newClass == boolean.class) {
                                throw new UnsupportedOperationException(
                                        "ValueAnnotatedTypeFactory: can't convert int to boolean");
                            } else {
                                newAnno =
                                        createIntRangeAnnotation(
                                                NumberUtils.castRange(newType, range));
                            }
                        } else {
                            List<?> values =
                                    ValueCheckerUtils.getValuesCastedToType(oldAnno, newType);
                            newAnno = resultAnnotationHandler(newType, values, tree);
                        }
                    } else {
                        List<?> values = ValueCheckerUtils.getValuesCastedToType(oldAnno, newType);
                        newAnno = resultAnnotationHandler(newType, values, tree);
                    }
                    atm.replaceAnnotation(newAnno);
                }
            } else if (atm.getKind() == TypeKind.ARRAY) {
                if (tree.getExpression().getKind() == Kind.NULL_LITERAL) {
                    atm.replaceAnnotation(BOTTOMVAL);
                }
            }
            return null;
        }

        /** Get the "value" field of the given annotation, casted to the given type. */
        private List<?> getValues(AnnotatedTypeMirror type, TypeMirror castTo) {
            AnnotationMirror anno = type.getAnnotationInHierarchy(UNKNOWNVAL);
            if (anno == null) {
                // If type is an AnnotatedTypeVariable (or other type without a primary annotation)
                // then anno will be null. It would be safe to use the annotation on the upper bound;
                // however, unless the upper bound was explicitly annotated, it will be unknown.
                // AnnotatedTypes.findEffectiveAnnotationInHierarchy(, toSearch, top)
                return new ArrayList<>();
            }
            return ValueCheckerUtils.getValuesCastedToType(anno, castTo);
        }

        @Override
        public Void visitLiteral(LiteralTree tree, AnnotatedTypeMirror type) {
            if (handledByValueChecker(type)) {
                switch (tree.getKind()) {
                    case BOOLEAN_LITERAL:
                        AnnotationMirror boolAnno =
                                createBooleanAnnotation(
                                        Collections.singletonList((Boolean) tree.getValue()));
                        type.replaceAnnotation(boolAnno);
                        return null;

                    case CHAR_LITERAL:
                        AnnotationMirror charAnno =
                                createCharAnnotation(
                                        Collections.singletonList((Character) tree.getValue()));
                        type.replaceAnnotation(charAnno);
                        return null;

                    case DOUBLE_LITERAL:
                        AnnotationMirror doubleAnno =
                                createNumberAnnotationMirror(
                                        Collections.<Number>singletonList(
                                                (Double) tree.getValue()));
                        type.replaceAnnotation(doubleAnno);
                        return null;

                    case FLOAT_LITERAL:
                        AnnotationMirror floatAnno =
                                createNumberAnnotationMirror(
                                        Collections.<Number>singletonList((Float) tree.getValue()));
                        type.replaceAnnotation(floatAnno);
                        return null;
                    case INT_LITERAL:
                        AnnotationMirror intAnno =
                                createNumberAnnotationMirror(
                                        Collections.<Number>singletonList(
                                                (Integer) tree.getValue()));
                        type.replaceAnnotation(intAnno);
                        return null;
                    case LONG_LITERAL:
                        AnnotationMirror longAnno =
                                createNumberAnnotationMirror(
                                        Collections.<Number>singletonList((Long) tree.getValue()));
                        type.replaceAnnotation(longAnno);
                        return null;
                    case STRING_LITERAL:
                        AnnotationMirror stringAnno =
                                createStringAnnotation(
                                        Collections.singletonList((String) tree.getValue()));
                        type.replaceAnnotation(stringAnno);
                        return null;
                    default:
                        return null;
                }
            }
            return null;
        }

        /**
         * Given a MemberSelectTree representing a method call, return true if the method's
         * declaration is annotated with {@code @StaticallyExecutable}.
         */
        private boolean methodIsStaticallyExecutable(Element method) {
            return getDeclAnnotation(method, StaticallyExecutable.class) != null;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, AnnotatedTypeMirror type) {
            if (handledByValueChecker(type)
                    && methodIsStaticallyExecutable(TreeUtils.elementFromUse(tree))) {
                // Get argument values
                List<? extends ExpressionTree> arguments = tree.getArguments();
                ArrayList<List<?>> argValues;
                if (arguments.size() > 0) {
                    argValues = new ArrayList<List<?>>();
                    for (ExpressionTree argument : arguments) {
                        AnnotatedTypeMirror argType = getAnnotatedType(argument);
                        List<?> values = getValues(argType, argType.getUnderlyingType());
                        if (values == null || values.isEmpty()) {
                            // values aren't known, so don't try to evaluate the
                            // method
                            return null;
                        }
                        argValues.add(values);
                    }
                } else {
                    argValues = null;
                }

                // Get receiver values
                AnnotatedTypeMirror receiver = getReceiverType(tree);
                List<?> receiverValues;

                if (receiver != null && !ElementUtils.isStatic(TreeUtils.elementFromUse(tree))) {
                    receiverValues = getValues(receiver, receiver.getUnderlyingType());
                    if (receiverValues == null || receiverValues.isEmpty()) {
                        // values aren't known, so don't try to evaluate the
                        // method
                        return null;
                    }
                } else {
                    receiverValues = null;
                }

                // Evaluate method
                List<?> returnValues =
                        evalutator.evaluateMethodCall(argValues, receiverValues, tree);
                AnnotationMirror returnType =
                        resultAnnotationHandler(type.getUnderlyingType(), returnValues, tree);
                type.replaceAnnotation(returnType);
            }

            return null;
        }

        @Override
        public Void visitNewClass(NewClassTree tree, AnnotatedTypeMirror type) {
            boolean wrapperClass =
                    TypesUtils.isBoxedPrimitive(type.getUnderlyingType())
                            || TypesUtils.isDeclaredOfName(
                                    type.getUnderlyingType(), "java.lang.String");

            if (wrapperClass
                    || (handledByValueChecker(type)
                            && methodIsStaticallyExecutable(TreeUtils.elementFromUse(tree)))) {
                // get arugment values
                List<? extends ExpressionTree> arguments = tree.getArguments();
                ArrayList<List<?>> argValues;
                if (arguments.size() > 0) {
                    argValues = new ArrayList<List<?>>();
                    for (ExpressionTree argument : arguments) {
                        AnnotatedTypeMirror argType = getAnnotatedType(argument);
                        List<?> values = getValues(argType, argType.getUnderlyingType());
                        if (values == null || values.isEmpty()) {
                            // values aren't known, so don't try to evaluate the
                            // method
                            return null;
                        }
                        argValues.add(values);
                    }
                } else {
                    argValues = null;
                }
                // Evaluate method
                List<?> returnValues =
                        evalutator.evaluteConstrutorCall(argValues, tree, type.getUnderlyingType());
                AnnotationMirror returnType =
                        resultAnnotationHandler(type.getUnderlyingType(), returnValues, tree);
                type.replaceAnnotation(returnType);
            }

            return null;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, AnnotatedTypeMirror type) {
            if (TreeUtils.isFieldAccess(tree) && handledByValueChecker(type)) {
                VariableElement elem = (VariableElement) InternalUtils.symbol(tree);
                Object value = elem.getConstantValue();
                if (value != null) {
                    // The field is a compile time constant.
                    type.replaceAnnotation(
                            resultAnnotationHandler(
                                    type.getUnderlyingType(),
                                    Collections.singletonList(value),
                                    tree));
                    return null;
                }
                if (ElementUtils.isStatic(elem) && ElementUtils.isFinal(elem)) {
                    // The field is static and final.
                    Element e = InternalUtils.symbol(tree.getExpression());
                    if (e != null) {
                        String classname = ElementUtils.getQualifiedClassName(e).toString();
                        String fieldName = tree.getIdentifier().toString();
                        value = evalutator.evaluateStaticFieldAccess(classname, fieldName, tree);
                        if (value != null) {
                            type.replaceAnnotation(
                                    resultAnnotationHandler(
                                            type.getUnderlyingType(),
                                            Collections.singletonList(value),
                                            tree));
                        }
                        return null;
                    }
                }

                if (tree.getIdentifier().toString().equals("length")) {
                    // The field acces is "someArrayExpression.length"
                    AnnotatedTypeMirror receiverType = getAnnotatedType(tree.getExpression());
                    if (receiverType.getKind() == TypeKind.ARRAY) {
                        AnnotationMirror arrayAnno = receiverType.getAnnotation(ArrayLen.class);
                        if (arrayAnno != null) {
                            // array.length, where array : @ArrayLen(x)
                            List<Integer> lengths =
                                    ValueAnnotatedTypeFactory.getArrayLength(arrayAnno);
                            type.replaceAnnotation(
                                    createNumberAnnotationMirror(new ArrayList<Number>(lengths)));
                            return null;
                        }
                    }
                }
            }
            return null;
        }

        /** Returns true iff the given type is in the domain of the Constant Value Checker. */
        private boolean handledByValueChecker(AnnotatedTypeMirror type) {
            return coveredClassStrings.contains(type.getUnderlyingType().toString());
        }

        /**
         * @param resultType is evaluated using getClass to derive a Class object
         * @param tree location for error reporting
         */
        private AnnotationMirror resultAnnotationHandler(
                TypeMirror resultType, List<?> results, Tree tree) {
            if (results == null) {
                return BOTTOMVAL;
            }
            Class<?> resultClass = ValueCheckerUtils.getClassFromType(resultType);

            // For some reason null is included in the list of values,
            // so remove it so that it does not cause a NPE elsewhere.
            results.remove(null);
            if (results.size() == 0) {
                return UNKNOWNVAL;
            } else if (resultClass == Boolean.class || resultClass == boolean.class) {
                HashSet<Boolean> boolVals = new HashSet<Boolean>(results.size());
                for (Object o : results) {
                    boolVals.add((Boolean) o);
                }
                return createBooleanAnnotation(new ArrayList<Boolean>(boolVals));

            } else if (resultClass == Double.class
                    || resultClass == double.class
                    || resultClass == Float.class
                    || resultClass == float.class
                    || resultClass == Integer.class
                    || resultClass == int.class
                    || resultClass == Long.class
                    || resultClass == long.class
                    || resultClass == Short.class
                    || resultClass == short.class
                    || resultClass == Byte.class
                    || resultClass == byte.class) {
                HashSet<Number> numberVals = new HashSet<>(results.size());
                List<Character> charVals = new ArrayList<>();
                for (Object o : results) {
                    if (o instanceof Character) {
                        charVals.add((Character) o);
                    } else {
                        numberVals.add((Number) o);
                    }
                }
                if (numberVals.isEmpty()) {
                    return createCharAnnotation(charVals);
                }
                return createNumberAnnotationMirror(new ArrayList<Number>(numberVals));
            } else if (resultClass == char.class || resultClass == Character.class) {
                HashSet<Character> intVals = new HashSet<>(results.size());
                for (Object o : results) {
                    if (o instanceof Number) {
                        intVals.add((char) ((Number) o).intValue());
                    } else {
                        intVals.add((char) o);
                    }
                }
                return createCharAnnotation(new ArrayList<Character>(intVals));
            } else if (resultClass == String.class) {
                HashSet<String> stringVals = new HashSet<String>(results.size());
                for (Object o : results) {
                    stringVals.add((String) o);
                }
                return createStringAnnotation(new ArrayList<String>(stringVals));
            } else if (resultClass == byte[].class) {
                HashSet<String> stringVals = new HashSet<String>(results.size());
                for (Object o : results) {
                    if (o instanceof byte[]) {
                        stringVals.add(new String((byte[]) o));
                    } else {
                        stringVals.add(o.toString());
                    }
                }
                return createStringAnnotation(new ArrayList<String>(stringVals));
            }

            return UNKNOWNVAL;
        }
    }

    /**
     * Create an {@code @IntVal} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createIntValAnnotation(List<Long> intValues) {
        if (intValues == null) {
            return BOTTOMVAL;
        }
        intValues = ValueCheckerUtils.removeDuplicates(intValues);
        if (intValues.isEmpty()) {
            return UNKNOWNVAL;
        } else if (intValues.size() > MAX_VALUES) {
            long valMin = Collections.min(intValues);
            long valMax = Collections.max(intValues);
            return createIntRangeAnnotation(new Range(valMin, valMax));
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, IntVal.class);
            builder.setValue("value", intValues);
            return builder.build();
        }
    }

    /**
     * Convert an {@code @IntRange} annotation to an {@code @IntVal} annotation, or to UNKNOWNVAL if
     * the input is too wide to be represented as an {@code @IntVal}.
     */
    public AnnotationMirror convertIntRangeToIntVal(AnnotationMirror intRangeAnno) {
        Range range = getIntRange(intRangeAnno);
        if (range.isWiderThan(MAX_VALUES)) {
            return UNKNOWNVAL;
        } else {
            List<Long> values = ValueCheckerUtils.getValuesFromRange(range, Long.class);
            return createIntValAnnotation(values);
        }
    }

    /**
     * Create a {@code @DoubleVal} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createDoubleValAnnotation(List<Double> doubleValues) {
        if (doubleValues == null) {
            return BOTTOMVAL;
        }
        doubleValues = ValueCheckerUtils.removeDuplicates(doubleValues);
        if (doubleValues.isEmpty() || doubleValues.size() > MAX_VALUES) {
            return UNKNOWNVAL;
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, DoubleVal.class);
            builder.setValue("value", doubleValues);
            return builder.build();
        }
    }

    /** Convert an {@code @IntVal} annotation to a {@code @DoubleVal} annotation. */
    private AnnotationMirror convertIntValToDoubleVal(AnnotationMirror intValAnno) {
        List<Long> intValues = getIntValues(intValAnno);
        return createDoubleValAnnotation(convertLongListToDoubleList(intValues));
    }

    /** Convert a {@code List&lt;Long&gt;} to a {@code List&lt;Double&gt;}. */
    private List<Double> convertLongListToDoubleList(List<Long> intValues) {
        List<Double> doubleValues = new ArrayList<Double>(intValues.size());
        for (Long intValue : intValues) {
            doubleValues.add(intValue.doubleValue());
        }
        return doubleValues;
    }

    /**
     * Create a {@code @StringVal} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createStringAnnotation(List<String> values) {
        if (values == null) {
            return BOTTOMVAL;
        }
        values = ValueCheckerUtils.removeDuplicates(values);
        if (values.isEmpty() || values.size() > MAX_VALUES) {
            return UNKNOWNVAL;
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, StringVal.class);
            builder.setValue("value", values);
            return builder.build();
        }
    }

    /**
     * Create an {@code @ArrayLen} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createArrayLenAnnotation(List<Integer> values) {
        if (values == null) {
            return BOTTOMVAL;
        }
        values = ValueCheckerUtils.removeDuplicates(values);
        if (values.isEmpty() || values.size() > MAX_VALUES) {
            return UNKNOWNVAL;
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, ArrayLen.class);
            builder.setValue("value", values);
            return builder.build();
        }
    }

    /**
     * Create a {@code @BoolVal} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createBooleanAnnotation(List<Boolean> values) {
        if (values == null) {
            return BOTTOMVAL;
        }
        values = ValueCheckerUtils.removeDuplicates(values);
        if (values.isEmpty() || values.size() > MAX_VALUES) {
            return UNKNOWNVAL;
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, BoolVal.class);
            builder.setValue("value", values);
            return builder.build();
        }
    }

    /**
     * Create an {@code @IntVal} annotation from the given set of values. May return BOTTOMVAL or
     * UNKNOWNVAL.
     */
    public AnnotationMirror createCharAnnotation(List<Character> values) {
        if (values == null) {
            return BOTTOMVAL;
        }
        values = ValueCheckerUtils.removeDuplicates(values);
        if (values.isEmpty()) {
            return UNKNOWNVAL;
        } else {
            List<Long> longValues = new ArrayList<>();
            for (char value : values) {
                longValues.add((long) value);
            }
            return createIntValAnnotation(longValues);
        }
    }

    /** @param values must be a homogeneous list: every element of it has the same class. */
    public AnnotationMirror createNumberAnnotationMirror(List<Number> values) {
        if (values == null) {
            return BOTTOMVAL;
        } else if (values.isEmpty()) {
            return UNKNOWNVAL;
        }
        Number first = values.get(0);
        if (first instanceof Integer
                || first instanceof Short
                || first instanceof Long
                || first instanceof Byte) {
            List<Long> intValues = new ArrayList<>();
            for (Number number : values) {
                intValues.add(number.longValue());
            }
            return createIntValAnnotation(intValues);
        } else if (first instanceof Double || first instanceof Float) {
            List<Double> intValues = new ArrayList<>();
            for (Number number : values) {
                intValues.add(number.doubleValue());
            }
            return createDoubleValAnnotation(intValues);
        }
        throw new UnsupportedOperationException(
                "ValueAnnotatedTypeFactory: unexpected class: " + first.getClass());
    }

    /**
     * Create an {@code @IntRange} annotation from the {@code from} and {@code to} values, which
     * must be valid ({@code from &le; to}).
     */
    public AnnotationMirror createIntRangeAnnotation(long from, long to) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, IntRange.class);
        builder.setValue("from", from);
        builder.setValue("to", to);
        return builder.build();
    }

    /**
     * Create an {@code @IntRange} annotation from the range. May return BOTTOMVAL or UNKNOWNVAL.
     */
    public AnnotationMirror createIntRangeAnnotation(Range range) {
        if (range.isNothing()) {
            return BOTTOMVAL;
        } else if (range.isEverything()) {
            return UNKNOWNVAL;
        } else {
            return createIntRangeAnnotation(range.from, range.to);
        }
    }

    /** Converts an {@code @IntVal} annotation to an {@code @IntRange} annotation. */
    public AnnotationMirror convertIntValToIntRange(AnnotationMirror intValAnno) {
        List<Long> intValues = getIntValues(intValAnno);
        return createIntRangeAnnotation(Collections.min(intValues), Collections.max(intValues));
    }

    public static Range getIntRange(AnnotationMirror rangeAnno) {
        // Assume rangeAnno is well-formed, i.e., 'from' is less than or equal to 'to'.
        return new Range(
                AnnotationUtils.getElementValue(rangeAnno, "from", Long.class, true),
                AnnotationUtils.getElementValue(rangeAnno, "to", Long.class, true));
    }

    public static List<Long> getIntValues(AnnotationMirror intAnno) {
        return AnnotationUtils.getElementValueArray(intAnno, "value", Long.class, true);
    }

    public static List<Double> getDoubleValues(AnnotationMirror doubleAnno) {
        return AnnotationUtils.getElementValueArray(doubleAnno, "value", Double.class, true);
    }

    public static List<Integer> getArrayLength(AnnotationMirror arrayAnno) {
        return AnnotationUtils.getElementValueArray(arrayAnno, "value", Integer.class, true);
    }

    public static List<Character> getCharValues(AnnotationMirror intAnno) {
        if (intAnno == null) {
            return new ArrayList<>();
        }
        List<Long> intValues =
                AnnotationUtils.getElementValueArray(intAnno, "value", Long.class, true);
        List<Character> charValues = new ArrayList<Character>();
        for (Long i : intValues) {
            charValues.add((char) i.intValue());
        }
        return charValues;
    }

    public static List<Boolean> getBooleanValues(AnnotationMirror boolAnno) {
        if (boolAnno == null) {
            return new ArrayList<>();
        }
        List<Boolean> boolValues =
                AnnotationUtils.getElementValueArray(boolAnno, "value", Boolean.class, true);
        Set<Boolean> boolSet = new TreeSet<>(boolValues);
        return new ArrayList<>(boolSet);
    }

    public List<Long> getIntValuesFromExpression(
            String expression, Tree tree, TreePath currentPath) {
        AnnotationMirror intValAnno = null;
        try {
            intValAnno =
                    getAnnotationFromJavaExpressionString(
                            expression, tree, currentPath, IntVal.class);
        } catch (FlowExpressionParseException e) {
            // ignore parse errors
            return null;
        }
        if (intValAnno == null) {
            return null;
        }

        return getIntValues(intValAnno);
    }
}
