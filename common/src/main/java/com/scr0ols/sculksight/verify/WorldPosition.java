package com.scr0ols.sculksight.verify;

/**
 * A plain world position: three ints, nothing else.
 *
 * <p>Exists so {@link IndexVerifier}'s diff can be written and tested against ordinary maps, with
 * no Minecraft type and no live level anywhere near it - the same reason {@link VerificationSample}
 * carries offsets as {@code int}s rather than a Minecraft position type.
 *
 * <p><b>Deliberately not {@code com.scr0ols.sculksight.client.SensorKey}</b>, which is the same
 * three ints for the same reason. That type belongs to the client source set, and a main-source-set
 * class importing it would be a dependency running the wrong way: {@code client} depends on the
 * verification package that lives here, never the reverse. The duplication is one three-field
 * record, not a formula worth sharing across a module boundary.
 */
public record WorldPosition(int x, int y, int z) {
}
