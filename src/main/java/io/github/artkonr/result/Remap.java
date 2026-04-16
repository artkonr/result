package io.github.artkonr.result;

import lombok.NonNull;

class Remap {
  
  static <N, E extends Exception> Result<N, E> returnRemapped(@NonNull Result<N, E> val) {
      return val;
  }
  
  static <V> V returnSupplied(@NonNull V val) {
      return val;
  }
  
}


