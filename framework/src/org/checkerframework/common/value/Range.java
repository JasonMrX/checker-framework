package org.checkerframework.common.value;

import java.math.BigInteger;

/**
 * The Range Class with mathematics operations. Models the range indicated by the @IntRange
 * annotation
 *
 * @author JasonMrX
 */
public class Range {

    /** The value 'from' */
    public final long from;

    /** The value 'to' */
    public final long to;

    public Range(long from, long to) {
        this.from = from;
        this.to = to;
    }

    public Range(long value) {
        this(value, value);
    }

    public Range() {
        this.from = Long.MIN_VALUE;
        this.to = Long.MAX_VALUE;
    }

    /**
     * Finds min and max in a given long array and return as a range.
     *
     * @param possibleValues the given long array
     * @return a range from min value to max value
     */
    private Range getRangeFromPossibleValues(long[] possibleValues) {
        long resultFrom = Long.MAX_VALUE;
        long resultTo = Long.MIN_VALUE;
        for (long pv : possibleValues) {
            resultFrom = Math.min(resultFrom, pv);
            resultTo = Math.max(resultTo, pv);
        }
        return new Range(resultFrom, resultTo);
    }

    /**
     * Unions two ranges into one. If there is no overlap between two ranges, the gap between the
     * two would be filled and thus results in only one single range
     *
     * @param right the range to union with this range
     * @return a range from the lowest possible value of the two ranges to the highest possible
     *     value of the two ranges
     */
    public Range union(Range right) {
        long resultFrom = Math.min(from, right.from);
        long resultTo = Math.max(to, right.to);
        return new Range(resultFrom, resultTo);
    }

    /**
     * Intersects two ranges. If there is no overlap between two ranges, a abnormal range with from
     * greater than to would be returned. This would be caught by
     * ValueAnnotatedTypeFactory.createIntRangeAnnotation when creating an annotation from range,
     * which would then be replaced with @UnknownVal
     *
     * @param right the range to intersect with this range
     * @return a range from
     */
    public Range intersect(Range right) {
        long resultFrom = Math.max(from, right.from);
        long resultTo = Math.min(to, right.to);
        return new Range(resultFrom, resultTo);
    }

    /**
     * Adds one range to another.
     *
     * @param right the range to be added to this range
     * @return the smallest single range that includes all possible values resulted from adding any
     *     two values selected from two ranges respectively
     */
    public Range plus(Range right) {
        long resultFrom = from + right.from;
        long resultTo = to + right.to;
        return new Range(resultFrom, resultTo);
    }

    /**
     * Subtracts one range from another
     *
     * @param right the range to be subtracted from this range
     * @return the smallest single range that includes all possible values resulted from subtracting
     *     an arbitrary value in @param from an arbitrary value in this range
     */
    public Range minus(Range right) {
        long resultFrom = from - right.to;
        long resultTo = to - right.from;
        return new Range(resultFrom, resultTo);
    }

    /**
     * Multiplies one range by another
     *
     * @param right the range to be multiplied by this range
     * @return the smallest single range that includes all possible values resulted from multiply an
     *     arbitrary value in @param by an arbitrary value in this range
     */
    public Range times(Range right) {
        long[] possibleValues = new long[4];
        possibleValues[0] = from * right.from;
        possibleValues[1] = from * right.to;
        possibleValues[2] = to * right.from;
        possibleValues[3] = to * right.to;
        return getRangeFromPossibleValues(possibleValues);
    }

    /**
     * Divides one range by another
     *
     * @param right the range to divide this range
     * @return the smallest range that includes all possible values resulted from dividing an
     *     arbitrary value in this range by an arbitrary value in @param
     */
    public Range divide(Range right) {
        long resultFrom = Long.MIN_VALUE;
        long resultTo = Long.MAX_VALUE;

        // TODO: be careful of divided by zero!
        if (from > 0 && right.from >= 0) {
            resultFrom = from / Math.max(right.to, 1);
            resultTo = to / Math.max(right.from, 1);
        } else if (from > 0 && right.to <= 0) {
            resultFrom = to / Math.min(right.to, -1);
            resultTo = from / Math.min(right.from, -1);
        } else if (from > 0) {
            resultFrom = -to;
            resultTo = to;
        } else if (to < 0 && right.from >= 0) {
            resultFrom = from / Math.max(right.from, 1);
            resultTo = to / Math.max(right.to, 1);
        } else if (to < 0 && right.to <= 0) {
            resultFrom = to / Math.min(right.from, -1);
            resultTo = from / Math.min(right.to, -1);
        } else if (to < 0) {
            resultFrom = from;
            resultTo = -from;
        } else if (right.from >= 0) {
            resultFrom = from / Math.max(right.from, 1);
            resultTo = to / Math.max(right.from, 1);
        } else if (right.to <= 0) {
            resultFrom = to / Math.min(right.to, -1);
            resultTo = from / Math.min(right.to, -1);
        } else {
            resultFrom = Math.min(from, -to);
            resultTo = Math.max(-from, to);
        }
        return new Range(resultFrom, resultTo);
    }

    /**
     * Modulos one range by another
     *
     * @param right the range to divide this range
     * @return a range (not the smallest one) that include all possible values resulted from taking
     *     the remainder from dividing an arbitrary value in this range by an arbitrary value
     *     in @param
     */
    public Range remainder(Range right) {
        long[] possibleValues = new long[9];
        possibleValues[0] = 0;
        possibleValues[1] = Math.min(from, Math.abs(right.from) - 1);
        possibleValues[2] = Math.min(from, Math.abs(right.to) - 1);
        possibleValues[3] = Math.min(to, Math.abs(right.from) - 1);
        possibleValues[4] = Math.min(to, Math.abs(right.to) - 1);
        possibleValues[5] = Math.max(from, -Math.abs(right.from) + 1);
        possibleValues[6] = Math.max(from, -Math.abs(right.to) + 1);
        possibleValues[7] = Math.max(to, -Math.abs(right.from) + 1);
        possibleValues[8] = Math.max(to, -Math.abs(right.to) + 1);
        return getRangeFromPossibleValues(possibleValues);
    }

    /**
     * Left shifts a range by another
     *
     * @param right the range of bits to be shifted
     * @return the range of the resulted value from left shifting an arbitrary value in this range
     *     by an arbitrary value in @param.
     */
    public Range shiftLeft(Range right) {
        // TODO: warning if right operand may be out of [0, 31]
        if (right.from < 0 || right.from > 31 || right.to < 0 || right.to > 31) {
            return new Range();
        }
        long resultFrom = from << (from >= 0 ? right.from : right.to);
        long resultTo = to << (to >= 0 ? right.to : right.from);
        return new Range(resultFrom, resultTo);
    }

    /**
     * Signed right shifts a range by another
     *
     * @param right the range of bits to be shifted
     * @return the range of the resulted value from signed right shifting an arbitrary value in this
     *     range by an arbitrary value in @param.
     */
    public Range signedShiftRight(Range right) {
        if (right.from < 0 || right.from > 31 || right.to < 0 || right.to > 31) {
            return new Range();
        }
        long resultFrom = from >> (from >= 0 ? right.to : right.from);
        long resultTo = to >> (to >= 0 ? right.from : right.to);
        return new Range(resultFrom, resultTo);
    }

    /**
     * Unary plus this range
     *
     * @return the resulted range of applying unary plus on an arbitrary value in this range
     */
    public Range unaryPlus() {
        return new Range(from, to);
    }

    /**
     * Unary minus this range
     *
     * @return the resulted range of applying unary minus on an arbitrary value in this range
     */
    public Range unaryMinus() {
        return new Range(-to, -from);
    }

    /**
     * Bitwise complements this range
     *
     * @return the resulted range of applying bitwise complement on an arbitrary value in this range
     */
    public Range bitwiseComplement() {
        return new Range(~to, ~from);
    }

    /**
     * Control flow refinement for less than operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range lessThan(Range right) {
        return new Range(from, Math.min(to, right.to - 1));
    }

    /**
     * Control flow refinement for less than or equal to operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range lessThanEq(Range right) {
        return new Range(from, Math.min(to, right.to));
    }

    /**
     * Control flow refinement for greater than operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range greaterThan(Range right) {
        return new Range(Math.max(from, right.from + 1), to);
    }

    /**
     * Control flow refinement for greater than or equal to operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range greaterThanEq(Range right) {
        return new Range(Math.max(from, right.from), to);
    }

    /**
     * Control flow refinement for equal to operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range equalTo(Range right) {
        return new Range(Math.max(from, right.from), Math.min(to, right.to));
    }

    /**
     * Control flow refinement for not equal to operator
     *
     * @param right the range to compare with
     * @return the refined result
     */
    public Range notEqualTo(Range right) {
        return new Range(from, to);
    }

    /**
     * Gets the number of possible values within this range. To prevent overflow, we use BigInteger
     * for calculation.
     *
     * @return
     */
    public BigInteger numberOfPossibleValues() {
        return BigInteger.valueOf(to).subtract(BigInteger.valueOf(from)).add(BigInteger.valueOf(1));
    }

    /**
     * Determine if the range is wider than a given value
     *
     * @param value
     * @return true if wider than the given value
     */
    public boolean isWiderThan(int value) {
        return numberOfPossibleValues().compareTo(BigInteger.valueOf(value)) == 1;
    }
}
