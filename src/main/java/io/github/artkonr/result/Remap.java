package io.github.artkonr.result;

import lombok.NonNull;

/**
 * Internal remapping functions to support lombok auto-generated checks.
 */
final class Remap {
  
  /**
  * Wraps to add a lombok-generated check to the argument.
  * @param <V> checked value type
  * @param val checked value
  * @return checked value
 */
  static <V> V returnSupplied(@NonNull V val) {
      return val;
  }
 
  /**
   * No-op constructor.
  */
  private Remap() { }
}


