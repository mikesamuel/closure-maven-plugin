package com.example.demo;

import com.example.demo.Wall.Update;
import com.example.demo.Wall.WallItem;
import com.example.demo.Wall.WallItems;
import com.google.common.base.Preconditions;

/**
 * A versioned wall with atomic mutators.
 * The Update proto is a snapshot of this.
 */
final class WallData {
  private int version = 0;
  private WallItems items = WallItems.newBuilder().build();

  /** An atomic snapshot. */
  public Update getWall() {
    WallItems currentItems;
    int currentVersion;
    synchronized (this) {
      currentItems = this.items;
      currentVersion = this.version;
    }
    return Update.newBuilder()
        .setItems(currentItems)
        .setVersion(currentVersion)
        .build();
  }

  /**
   * The current version.  Monotonic so can be compared to versions packaged
   * with a request to decide whether its worth rendering the items.
   */
  public synchronized int getVersion() {
    return version;
  }

  /** Atomically adds an item and bumps the version. */
  public Update addItem(WallItem newItem) {
    Preconditions.checkNotNull(newItem);

    WallItems currentItems;
    int currentVersion;
    synchronized (this) {
      // Ensure monotonicity.
      Preconditions.checkState(this.version < Integer.MAX_VALUE);
      WallItems newItems = this.items.toBuilder().addItem(newItem).build();

      currentItems = this.items = newItems;
      currentVersion = ++this.version;
    }
    return Update.newBuilder()
        .setItems(currentItems)
        .setVersion(currentVersion)
        .build();
  }
}