package com.google.common.html.plugin.plan;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * A persisted store of hashes that can be used to compare previous builds of
 * {@link Step}s to decide whether to rebuild.
 */
public final class HashStore {
  private final ConcurrentHashMap<String, Hash> hashes
      = new ConcurrentHashMap<String, Hash>();

  /** The previous hash of a step's inputs if available. */
  Optional<Hash> getHash(String stepKey) {
    return Optional.fromNullable(hashes.get(stepKey));
  }

  /** Store the hash of a step's inputs. */
  void setHash(String stepKey, Hash hash) {
    hashes.put(
        Preconditions.checkNotNull(stepKey), Preconditions.checkNotNull(hash));
  }

  public static HashStore read(Reader in, Log log) throws IOException {
    HashStore hs = new HashStore();

    JSONParser p = new JSONParser();
    Object result;
    try {
      result = p.parse(in);
    } catch (ParseException ex) {
      throw new IOException("Malformed hash store", ex);
    }
    boolean wellFormed = true;
    if (result instanceof Map<?, ?>) {
      for (Map.Entry<?, ?> e : ((Map<?, ?>) result).entrySet()) {
        Object k = e.getKey();
        Object v = e.getValue();
        if (!(k instanceof String)) {
          log.error(
              "Bad hash store key " + k + " : "
              + (k != null ? k.getClass() : null));
          wellFormed = false;
          continue;
        }
        if (!(v instanceof String)) {
          log.error("Bad hash store value " + v + " : "
              + (v != null ? v.getClass() : null));
          wellFormed = false;
          continue;
        }
        Hash h = new Hash((String) v);
        Hash old = hs.hashes.put((String) k, h);
        if (old != null) {
          log.error("Multiple hashes for key " + k);
          wellFormed = false;
        }
      }
    } else {
      log.error("Hash store was not a JSON map");
      wellFormed = false;
    }
    if (!wellFormed) {
      throw new IOException("Malformed hash store");
    }
    return hs;
  }

  public void write(Writer out) throws IOException {
    JSONObject.writeJSONString(this.hashes, out);
  }
}
