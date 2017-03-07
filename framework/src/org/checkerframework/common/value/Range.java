package org.checkerframework.common.value;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Range class models a 64-bit 2s-complement integral interval, such as all integers between 1
 * and 10, inclusive. Ranges are immutable.
 *
 * @author JasonMrX
 */
public class Range {

    /** The lower bound of the interval, inclusive. */
    public final long from;

    /** The upper bound of the interval, inclusive. */
    public final long to;

    /** A range containing all possible values. */
    public static final Range EVERYTHING = new Range(Long.MIN_VALUE, Long.MAX_VALUE);

    public static final Range INT_EVERYTHING = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final Range SHORT_EVERYTHING = new Range(Short.MIN_VALUE, Short.MAX_VALUE);

    /** The empty range. */
    public static final Range NOTHING = new Range(true);

    /**
     * Constructs a range with its bounds specified by two parameters, {@code from} and {@code to}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (inclusive)
     */
    public Range(long from, long to) {
        if (!(from <= to)) {
            throw new IllegalArgumentException(String.format("Invalid Range: %s %s", from, to));
        }
        this.from = from;
        this.to = to;
    }

    /** Creates the singleton empty range. */
    private Range(boolean isEmpty) {
        if (!isEmpty) {
            throw new IllegalArgumentException();
        }
        this.from = Long.MAX_VALUE;
        this.to = Long.MIN_VALUE;
    }

    /**
     * Returns a range with its bounds specified by two parameters, {@code from} and {@code to}. If
     * {@code from} is greater than {@code to}, returns {@link #NOTHING}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (inclusive)
     */
    private Range createRangeOrNothing(long from, long to) {
        if (from <= to) {
            return new Range(from, to);
        } else {
            return NOTHING;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s..%s]", from, to);
    }

    /** Return true if this range contains every {@code long} value. */
    public boolean isEverything() {
        return from == Long.MIN_VALUE && to == Long.MAX_VALUE;
    }

    /** Return true if this range contains no values. */
    public boolean isNothing() {
        return this == NOTHING;
    }

    /**
     * Converts a this range to a 32-bit integral range.
     *
     * <p>If the this range are out of the Integer type scope, convert the bounds to Integer type in
     * accordance with Java overflow rules, e.g., Integer.MAX_VALUE + 1 is converted to
     * Integer.MIN_VALUE.
     *
     * <p>If this range is too wide, i.e., wider than the full range of the Integer class, return
     * INT_EVERYTHING.
     */
    public Range intRange() {
        if (this.isWiderThan((long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE + 1)) {
            return INT_EVERYTHING;
        } else {
            int intFrom = (int) this.from;
            int intTo = (int) this.to;
            if (intFrom <= intTo) {
                return new Range(intFrom, intTo);
            } else {
                return INT_EVERYTHING;
            }
        }
    }

    /**
     * Converts a this range to a 16-bit short range.
     *
     * <p>If the this range are out of the Short type scope, convert the bounds to Short type in
     * accordance with Java overflow rules, e.g., Short.MAX_VALUE + 1 is converted to
     * Short.MIN_VALUE.
     *
     * <p>If this range is too wide, i.e., wider than the full range of the Short class, return
     * SHORT_EVERYTHING.
     */
    public Range shortRange() {
        if (this.isWiderThan(Short.MAX_VALUE - Short.MIN_VALUE + 1)) {
            // short is be promoted to int before the operation so no need for explicit casting
            return SHORT_EVERYTHING;
        } else {
            short shortFrom = (short) this.from;
            short shortTo = (short) this.to;
            if (shortFrom <= shortTo) {
                return new Range(shortFrom, shortTo);
            } else {
                return SHORT_EVERYTHING;
            }
        }
    }

    /** Returns true if the element is contained in this range. */
    public boolean contains(long element) {
        return from <= element && element <= to;
    }

    /**
     * Returns the smallest range that includes all values contained in either of the two ranges. We
     * call this the union of two ranges.
     *
     * @param right a range to union with this range
     * @return a range resulting from the union of the specified range and this range
     */
    public Range union(Range right) {
        if (this.isNothing()) {
            return right;
        } else if (right.isNothing()) {
            return this;
        }

        long resultFrom = Math.min(from, right.from);
        long resultTo = Math.max(to, right.to);
        return new Range(resultFrom, resultTo);
    }

    /**
     * Returns the smallest range that includes all values contained in both of the two ranges. We
     * call this the intersection of two ranges. If there is no overlap between the two ranges,
     * returns an empty range.
     *
     * @param right the range to intersect with this range
     * @return a range resulting from the intersection of the specified range and this range
     */
    public Range intersect(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        long resultFrom = Math.max(from, right.from);
        long resultTo = Math.min(to, right.to);
        return createRangeOrNothing(resultFrom, resultTo);
    }

    /**
     * Returns the smallest range that includes all possible values resulting from adding an
     * arbitrary value in the specified range to an arbitrary value in this range. We call this We
     * call this the addition of two ranges.
     *
     * @param right a range to be added to this range
     * @return the range resulting from the addition of the specified range and this range
     */
    public Range plus(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (this.isWithinInteger() && right.isWithinInteger()) {
            long resultFrom = from + right.from;
            long resultTo = to + right.to;
            return new Range(resultFrom, resultTo);
        } else {
            BigInteger bigFrom = BigInteger.valueOf(from).add(BigInteger.valueOf(right.from));
            BigInteger bigTo = BigInteger.valueOf(to).add(BigInteger.valueOf(right.to));
            return bigRangeToLongRange(bigFrom, bigTo);
        }
    }

    /**
     * Returns the smallest range that includes all possible values resulting from subtracting an
     * arbitrary value in the specified range from an arbitrary value in this range. We call this
     * the subtraction of two ranges.
     *
     * @param right the range to be subtracted from this range
     * @return the range resulting from subtracting the specified range from this range
     */
    public Range minus(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (this.isWithinInteger() && right.isWithinInteger()) {
            long resultFrom = from - right.to;
            long resultTo = to - right.from;
            return new Range(resultFrom, resultTo);
        } else {
            BigInteger bigFrom = BigInteger.valueOf(from).subtract(BigInteger.valueOf(right.to));
            BigInteger bigTo = BigInteger.valueOf(to).subtract(BigInteger.valueOf(right.from));
            return bigRangeToLongRange(bigFrom, bigTo);
        }
    }

    /**
     * Returns the smallest range that includes all possible values resulting from multiplying an
     * arbitrary value in the specified range by an arbitrary value in this range. We call this the
     * multiplication of two ranges.
     *
     * @param right the specified range to be multiplied by this range
     * @return the range resulting from multiplying the specified range by this range
     */
    public Range times(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (this.isWithinInteger() && right.isWithinInteger()) {
            List<Long> possibleValues =
                    Arrays.asList(
                            from * right.from, from * right.to, to * right.from, to * right.to);
            return new Range(Collections.min(possibleValues), Collections.max(possibleValues));
        } else {
            List<BigInteger> bigPossibleValues =
                    Arrays.asList(
                            BigInteger.valueOf(from).multiply(BigInteger.valueOf(right.from)),
                            BigInteger.valueOf(from).multiply(BigInteger.valueOf(right.to)),
                            BigInteger.valueOf(to).multiply(BigInteger.valueOf(right.from)),
                            BigInteger.valueOf(to).multiply(BigInteger.valueOf(right.to)));
            BigInteger bigFrom = Collections.min(bigPossibleValues);
            BigInteger bigTo = Collections.max(bigPossibleValues);
            return bigRangeToLongRange(bigFrom, bigTo);
        }
    }

    /**
     * Returns the smallest range that includes all possible values resulting from dividing (integer
     * division) an arbitrary value in this range by an arbitrary value in the specified range. We
     * call this the division of two ranges.
     *
     * @param right the specified range by which this range is divided
     * @return the range resulting from dividing this range by the specified range
     */
    public Range divide(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }
        // Special cases that involve overflow.
        // The only overflow in integer division is Long.MIN_VALUE / -1 == Long.MIN_VALUE.
        if (from == Long.MIN_VALUE && right.contains(-1)) {
            // The values in the right range are all negative because right does not contain 0 but
            // does contain 1.
            if (from != to) {
                // Special case 1:
                // This range contains Long.MIN_VALUE and Long.MIN_VALUE + 1, which makes the
                // result range EVERYTHING.
                return EVERYTHING;
            } else if (right.from != right.to) {
                // Special case 2:
                // This range contains only Long.MIN_VALUE, and the right range contains at least -1
                // and -2. The result range is from Long.MIN_VALUE to Long.MIN_VALUE / -2.
                return new Range(Long.MIN_VALUE, Long.MIN_VALUE / -2);
            } else {
                // Special case 3:
                // This range contains only Long.MIN_VALUE, and right contains only -1.
                return new Range(Long.MIN_VALUE, Long.MIN_VALUE);
            }
        }
        long resultFrom;
        long resultTo;
        // We needn't worry about the overflow issue starting from here.
        // To facilitate the calculation of the result range, we categorize all the scenarios into 9
        // different cases:
        if (from > 0 && right.from >= 0) {
            // 1. this: po, right: nn
            resultFrom = from / Math.max(right.to, 1);
            resultTo = to / Math.max(right.from, 1);
        } else if (from > 0 && right.to <= 0) {
            // 2. this: po, right: np
            resultFrom = to / Math.min(right.to, -1);
            resultTo = from / Math.min(right.from, -1);
        } else if (from > 0) {
            // 3. this: po, right: us
            resultFrom = -to;
            resultTo = to;
        } else if (to < 0 && right.from >= 0) {
            // 4. this: ne, right: nn
            resultFrom = from / Math.max(right.from, 1);
            resultTo = to / Math.max(right.to, 1);
        } else if (to < 0 && right.to <= 0) {
            // 5. this: ne, right: np
            resultFrom = to / Math.min(right.from, -1);
            resultTo = from / Math.min(right.to, -1);
        } else if (to < 0) {
            // 6. this: ne, right: us
            resultFrom = from;
            resultTo = -from;
        } else if (right.from >= 0) {
            // 7. this: us, right: nn
            resultFrom = from / Math.max(right.from, 1);
            resultTo = to / Math.max(right.from, 1);
        } else if (right.to <= 0) {
            // 8. this: us, right: np
            resultFrom = to / Math.min(right.to, -1);
            resultTo = from / Math.min(right.to, -1);
        } else {
            // 9. this: us, right: us
            resultFrom = Math.min(from, -to);
            resultTo = Math.max(-from, to);
        }
        // (note: po=>positive, ne=>negative, us:=>unknown sign, np=>non-positive, nn=>non-negative)
        return new Range(resultFrom, resultTo);
    }

    /**
     * Returns a range that includes all possible values of the remainder of dividing an arbitrary
     * value in this range by an arbitrary value in the specified range. Note that this range might
     * not be the smallest range that includes all the possible values.
     *
     * @param right the specified range by which this range is divided
     * @return the range of the remainder of dividing this range by the specified range
     */
    public Range remainder(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }
        // Special cases that would cause overflow if we use the general method below
        if (right.from == Long.MIN_VALUE) {
            Range range;
            // The value Long.MIN_VALUE as a divider needs special handling as follows:
            if (from > Long.MIN_VALUE) {
                // When this range doesn't contain Long.MIN_VALUE, the remainder of each value
                // in this range divided by Long.MIN_VALUE is this value itself. Therefore the
                // result range is this range itself.
                range = new Range(from, to);
            } else if (from == Long.MIN_VALUE && to > Long.MIN_VALUE) {
                // When this range contains Long.MIN_VALUE, which would have a remainder of 0 if
                // divided by Long.MIN_VALUE, the result range is {0} union with [from + 1, to]
                range = (new Range(from + 1, to)).union(new Range(0, 0));
            } else {
                // When this range only contains Long.MIN_VALUE, the result range is {0}
                range = new Range(0, 0);
            }
            // If right.to > Long.MIN_VALUE, union the previous result with the result of range
            // [right.from + 1, right.to] divided by this range, which can be calculated using
            // the general method (see below)
            if (right.to > Long.MIN_VALUE) {
                Range rangeAdditional = this.remainder(new Range(right.from + 1, right.to));
                range = range.union(rangeAdditional);
            }
            return range;
        }
        // General method:
        // Calculate range1: the result range of this range divided by EVERYTHING. For example,
        // if this range is [3, 5], then the result range would be [0, 5]. If this range is [-3, 4],
        // then the result range would be [-3, 4]. In general, the result range is {0} union with
        // this range excluding the value Long.MIN_VALUE.
        Range range1 =
                (new Range(Math.max(Long.MIN_VALUE + 1, from), Math.max(Long.MIN_VALUE + 1, to)))
                        .union(new Range(0, 0));
        // Calculate range2: the result range of range EVERYTHING divided by the right range. For
        // example, if the right range is [-5, 3], then the result range would be [-4, 4]. If the
        // right range is [3, 6], then the result range would be [-5, 5]. In general, the result
        // range is calculated as following:
        Range range2 =
                Math.abs(right.from) > Math.abs(right.to)
                        ? new Range(-Math.abs(right.from) + 1, Math.abs(right.from) - 1)
                        : new Range(-Math.abs(right.to) + 1, Math.abs(right.to) - 1);
        // Since range1 and range2 are both super sets of the minimal result range, we return the
        // intersect of range1 and range2, which is correct (super set) and precise enough.
        return range1.intersect(range2);
    }

    /**
     * Returns a range that includes all possible values resulting from left shifting an arbitrary
     * value in this range by an arbitrary number of bits in the specified range. We call this the
     * left shift operation of a range.
     *
     * @param right the range of bits by which this range is left shifted
     * @return the range resulting from left shifting this range by the specified range
     */
    public Range shiftLeft(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (this.isWithinInteger() && right.from >= 0 && right.to <= 31) {
            // The long type can handle the cases with shift distance no longer than 31.
            long resultFrom = from << (from >= 0 ? right.from : right.to);
            long resultTo = to << (to >= 0 ? right.to : right.from);
            return new Range(resultFrom, resultTo);
        } else if (right.from >= 0 && right.to <= 63) {
            // If the shift distance is within 0 to 63, the calculation can be handled by BigInteger.
            BigInteger bigFrom =
                    BigInteger.valueOf(from)
                            .shiftLeft(from >= 0 ? (int) right.from : (int) right.to);
            BigInteger bigTo =
                    BigInteger.valueOf(to).shiftLeft(to >= 0 ? (int) right.to : (int) right.from);
            return bigRangeToLongRange(bigFrom, bigTo);
        } else {
            // In other cases, we give up the calculation and return EVERYTHING (rare in practice).
            return EVERYTHING;
        }
    }

    /**
     * Returns a range that includes all possible values resulting from signed right shifting an
     * arbitrary value in this range by an arbitrary number of bits in the specified range. We call
     * this the signed right shift operation of a range.
     *
     * @param right the range of bits by which this range is signed right shifted
     * @return the range resulting from signed right shifting this range by the specified range
     */
    public Range signedShiftRight(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (this.isWithinInteger() && right.from >= 0 && right.to <= 31) {
            // The long type can handle the cases with shift distance no longer than 31
            long resultFrom = from >> (from >= 0 ? right.to : right.from);
            long resultTo = to >> (to >= 0 ? right.from : right.to);
            return new Range(resultFrom, resultTo);
        } else {
            // Signed shift right operation for long type cannot be simulated with BigInteger.
            // Give up the calculation and return EVERYTHING instead.
            return EVERYTHING;
        }
    }

    /**
     * Returns the range of a variable that falls within this range after applying the unary plus
     * operation (which is a no-op).
     *
     * @return this range
     */
    public Range unaryPlus() {
        return this;
    }

    /**
     * Returns the range of a variable that falls within this range after applying unary minus
     * operation.
     *
     * @return the resulted range of applying unary minus on an arbitrary value in this range
     */
    public Range unaryMinus() {
        if (this.isNothing()) {
            return NOTHING;
        }

        if (from == Long.MIN_VALUE && from != to) {
            // the only case that needs special handling because of overflow
            return EVERYTHING;
        }

        return new Range(-to, -from);
    }

    /**
     * Returns the range of a variable that falls within this range after applying bitwise
     * complement operation.
     *
     * @return the resulted range of applying bitwise complement on an arbitrary value in this range
     */
    public Range bitwiseComplement() {
        if (this.isNothing()) {
            return NOTHING;
        }

        return new Range(~to, ~from);
    }

    /**
     * Refines this range to reflect that some value in it can be less than a value in the given
     * range. This is used for calculating the control-flow-refined result of the &lt; operator. For
     * example:
     *
     * <pre>
     * <code>
     *     {@literal @}IntRange(from = 0, to = 10) int a;
     *     {@literal @}IntRange(from = 3, to = 7) int b;
     *     ...
     *     if (a &lt; b) {
     *         // range of <i>a</i> is now refined to [0, 6] because a value in range [7, 10]
     *         // cannot be smaller than variable <i>b</i> with range [3, 7].
     *         ...
     *     }
     * </code>
     * </pre>
     *
     * Use the {@link #refineGreaterThanEq(Range)} method if you are also interested in refining the
     * given range
     *
     * @param right the specified {@code Range} to compare with
     * @return the refined {@code Range}
     */
    public Range refineLessThan(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (right.to == Long.MIN_VALUE) {
            return NOTHING;
        }

        long resultTo = Math.min(to, right.to - 1);
        return createRangeOrNothing(from, resultTo);
    }

    /**
     * Refines this range to reflect that some value in it can be less than or equal to a value in
     * the given range. This is used for calculating the control-flow-refined result of the &lt;=
     * operator. For example:
     *
     * <pre>
     * <code>
     *     {@literal @}IntRange(from = 0, to = 10) int a;
     *     {@literal @}IntRange(from = 3, to = 7) int b;
     *     ...
     *     if (a &lt;= b) {
     *         // range of <i>a</i> is now refined to [0, 7] because a value in range [8, 10]
     *         // cannot be less than or equal to variable <i>b</i> with range [3, 7].
     *         ...
     *     }
     * </code>
     * </pre>
     *
     * Use the {@link #refineGreaterThan(Range)} method if you are also interested in refining the
     * given range
     *
     * @param right the specified {@code Range} to compare with
     * @return the refined {@code Range}
     */
    public Range refineLessThanEq(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        long resultTo = Math.min(to, right.to);
        return createRangeOrNothing(from, resultTo);
    }

    /**
     * Refines this range to reflect that some value in it can be greater than a value in the given
     * range. This is used for calculating the control-flow-refined result of the &gt; operator. For
     * example:
     *
     * <pre>
     * <code>
     *     {@literal @}IntRange(from = 0, to = 10) int a;
     *     {@literal @}IntRange(from = 3, to = 7) int b;
     *     ...
     *     if (a &gt; b) {
     *         // range of <i>a</i> is now refined to [4, 10] because a value in range [0, 3]
     *         // cannot be greater than variable <i>b</i> with range [3, 7].
     *         ...
     *     }
     * </code>
     * </pre>
     *
     * Use the {@link #refineLessThanEq(Range)} method if you are also interested in refining the
     * given range
     *
     * @param right the specified {@code Range} to compare with
     * @return the refined {@code Range}
     */
    public Range refineGreaterThan(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        if (right.from == Long.MAX_VALUE) {
            return NOTHING;
        }

        long resultFrom = Math.max(from, right.from + 1);
        return createRangeOrNothing(resultFrom, to);
    }

    /**
     * Refines this range to reflect that some value in it can be greater than or equal to a value
     * in the given range. This is used for calculating the control-flow-refined result of the &gt;=
     * operator. For example:
     *
     * <pre>
     * <code>
     *     {@literal @}IntRange(from = 0, to = 10) int a;
     *     {@literal @}IntRange(from = 3, to = 7) int b;
     *     ...
     *     if (a &gt;= b) {
     *         // range of <i>a</i> is now refined to [3, 10] because a value in range [0, 2]
     *         // cannot be greater than or equal to variable <i>b</i> with range [3, 7].
     *         ...
     *     }
     * </code>
     * </pre>
     *
     * Use the {@link #refineLessThan(Range)} method if you are also interested in refining the
     * given range
     *
     * @param right the specified {@code Range} to compare with
     * @return the refined {@code Range}
     */
    public Range refineGreaterThanEq(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        long resultFrom = Math.max(from, right.from);
        return createRangeOrNothing(resultFrom, to);
    }

    /**
     * Refines this range to reflect that some value in it can be equal to a value in the given
     * range. This is used for calculating the control-flow-refined result of the == operator. For
     * example:
     *
     * <pre>
     * <code>
     *     {@literal @}IntRange(from = 0, to = 10) int a;
     *     {@literal @}IntRange(from = 3, to = 15) int b;
     *     ...
     *     if (a == b) {
     *         // range of <i>a</i> is now refined to [3, 10] because a value in range [0, 2]
     *         // cannot be equal to variable <i>b</i> with range [3, 15].
     *         ...
     *     }
     * </code>
     * </pre>
     *
     * @param right the specified {@code Range} to compare with
     * @return the refined {@code Range}
     */
    public Range refineEqualTo(Range right) {
        if (this.isNothing() || right.isNothing()) {
            return NOTHING;
        }

        long resultFrom = Math.max(from, right.from);
        long resultTo = Math.min(to, right.to);
        return createRangeOrNothing(resultFrom, resultTo);
    }

    /**
     * Determines if the range is wider than a given value, i.e., if the number of possible values
     * enclosed by this range is more than the given value.
     *
     * @param value the value to compare with
     * @return true if wider than the given value
     */
    public boolean isWiderThan(long value) {
        return BigInteger.valueOf(to)
                        .subtract(BigInteger.valueOf(from))
                        .add(BigInteger.ONE)
                        .compareTo(BigInteger.valueOf(value))
                == 1;
    }

    /**
     * Determines if this range is completely contained in the scope of the Integer type.
     *
     * @return true if the range is completely contained in the scope of the Integer type
     */
    private boolean isWithinInteger() {
        return from >= Integer.MIN_VALUE && to <= Integer.MAX_VALUE;
    }

    private static final BigInteger longPossibleValues =
            BigInteger.valueOf(Long.MAX_VALUE)
                    .subtract(BigInteger.valueOf(Long.MIN_VALUE))
                    .add(BigInteger.ONE);

    /**
     * Converts a range with BigInteger type bounds to a range with Long type bounds.
     *
     * <p>If the BigInteger bounds are out of the Long type scope, convert the bounds to Long type
     * in accordance with Java overflow rules, e.g., Long.MAX_VALUE + 1 is converted to
     * Long.MIN_VALUE.
     *
     * <p>If the BigInteger range is too wide, i.e., wider than the full range of the Long class,
     * return EVERYTHING.
     *
     * @param bigFrom the lower bound of the BigInteger range
     * @param bigTo the upper bound of the BigInteger range
     * @return a range with Long type bounds converted from the BigInteger range
     */
    private static Range bigRangeToLongRange(BigInteger bigFrom, BigInteger bigTo) {
        BigInteger numValues = bigTo.subtract(bigFrom).add(BigInteger.ONE);
        if (numValues.compareTo(longPossibleValues) == 1) {
            return EVERYTHING;
        } else {
            long resultFrom = bigFrom.longValue();
            long resultTo = bigTo.longValue();
            if (resultFrom <= resultTo) {
                return new Range(resultFrom, resultTo);
            } else {
                return EVERYTHING;
            }
        }
    }
}
