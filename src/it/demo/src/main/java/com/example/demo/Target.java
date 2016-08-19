package com.example.demo;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/** Decomposed from the URI path. */
final class Target {
  final Optional<Nonce> nonce;
  /** Path elements following any nonce. */
  final String suffix;

  private Target(Optional<Nonce> nonce, String suffix) {
    this.nonce = nonce;
    this.suffix = suffix;
  }

  static Target create(String absUriPath) {
    Preconditions.checkArgument(absUriPath.startsWith("/"));
    Optional<Nonce> nonceOpt = Optional.absent();

    int lastSlash = absUriPath.lastIndexOf('/');
    if (lastSlash >= 1) {
      int prevSlash = absUriPath.lastIndexOf('/', lastSlash - 1);
      if (prevSlash == 0) {
        String betweenSlashes = absUriPath.substring(1, lastSlash);
        if (Nonce.isValidNonceText(betweenSlashes)) {
          nonceOpt = Optional.of(new Nonce(betweenSlashes));
        }
      }
    }

    return new Target(
        nonceOpt,

        (nonceOpt.isPresent()
         ? absUriPath.substring(nonceOpt.get().text.length() + 2)
         : absUriPath));
  }
}