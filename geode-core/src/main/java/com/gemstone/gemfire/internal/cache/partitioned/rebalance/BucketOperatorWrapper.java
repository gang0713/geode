package com.gemstone.gemfire.internal.cache.partitioned.rebalance;

import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.gemstone.gemfire.internal.cache.control.PartitionRebalanceDetailsImpl;
import com.gemstone.gemfire.internal.cache.control.ResourceManagerStats;
import com.gemstone.gemfire.internal.logging.LogService;

public class BucketOperatorWrapper implements BucketOperator {
  private static final Logger logger = LogService.getLogger();
  
  private final BucketOperator delegate;
  private final Set<PartitionRebalanceDetailsImpl> detailSet;
  private final int regionCount;
  private final ResourceManagerStats stats;
  private final PartitionedRegion leaderRegion;

  public BucketOperatorWrapper(BucketOperator delegate, Set<PartitionRebalanceDetailsImpl> rebalanceDetails, 
      ResourceManagerStats stats, PartitionedRegion leaderRegion) {
    this.delegate = delegate;
    this.detailSet = rebalanceDetails;
    this.regionCount = detailSet.size();
    this.stats = stats;
    this.leaderRegion = leaderRegion;
  }

  @Override
  public boolean moveBucket(InternalDistributedMember sourceMember, 
      InternalDistributedMember targetMember, int id, 
      Map<String, Long> colocatedRegionBytes) {
    long start = System.nanoTime();
    boolean result = false;
    long elapsed = 0;
    long totalBytes = 0;

    if (stats != null) {
      stats.startBucketTransfer(regionCount);
    }
    try {
      result = delegate.moveBucket(sourceMember, targetMember, id, colocatedRegionBytes);
      elapsed = System.nanoTime() - start;
      if (result) {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} bucket {} moved from {} to {}", leaderRegion, id, sourceMember, targetMember);
        }
        for (PartitionRebalanceDetailsImpl details : detailSet) {
          String regionPath = details.getRegionPath();
          Long regionBytes = colocatedRegionBytes.get(regionPath);
          if (regionBytes != null) {
            // only increment the elapsed time for the leader region
            details.incTransfers(regionBytes.longValue(), 
                details.getRegion().equals(leaderRegion) ? elapsed : 0);
            totalBytes += regionBytes.longValue();
          }
        }
      } else {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} bucket {} moved failed from {} to {}", leaderRegion, id, sourceMember, targetMember);
        }
      }
    } finally {
      if (stats != null) {
        stats.endBucketTransfer(regionCount, result, totalBytes, elapsed);
      }
    }

    return result;
  }

  @Override
  public void createRedundantBucket(
      final InternalDistributedMember targetMember, final int i, 
      final Map<String, Long> colocatedRegionBytes, final Completion completion) {

    if (stats != null) {
      stats.startBucketCreate(regionCount);
    }

    final long start = System.nanoTime();
    delegate.createRedundantBucket(targetMember, i, 
        colocatedRegionBytes, new Completion() {

      @Override
      public void onSuccess() {
        long totalBytes = 0;
        long elapsed = System.nanoTime() - start;
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} redundant bucket {} created on {}", leaderRegion, i, targetMember);
        }
        for (PartitionRebalanceDetailsImpl details : detailSet) {
          String regionPath = details.getRegionPath();
          Long lrb = colocatedRegionBytes.get(regionPath);
          if (lrb != null) { // region could have gone away - esp during shutdow
            long regionBytes = lrb.longValue();
            // Only add the elapsed time to the leader region.
            details.incCreates(regionBytes, details.getRegion().equals(leaderRegion) ? elapsed : 0);
            totalBytes += regionBytes;
          }
        }

        if (stats != null) {
          stats.endBucketCreate(regionCount, true, totalBytes, elapsed);
        }
        
        //invoke onSuccess on the received completion callback
        completion.onSuccess();
      }

      @Override
      public void onFailure() {
        long elapsed = System.nanoTime() - start;

        if (logger.isDebugEnabled()) {
          logger.info("Rebalancing {} redundant bucket {} failed creation on {}", leaderRegion, i, targetMember);
        }

        if (stats != null) {
          stats.endBucketCreate(regionCount, false, 0, elapsed);
        }
        
        //invoke onFailure on the received completion callback
        completion.onFailure();
      }
    });
  }

  @Override
  public boolean removeBucket(
      InternalDistributedMember targetMember, int i, 
      Map<String, Long> colocatedRegionBytes) {
    boolean result = false;
    long elapsed = 0;
    long totalBytes = 0;

    if (stats != null) {
      stats.startBucketRemove(regionCount);
    }
    try {
      long start = System.nanoTime();
      result = delegate.removeBucket(targetMember, i, colocatedRegionBytes);
      elapsed = System.nanoTime() - start;
      if (result) {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} redundant bucket {} removed from {}", leaderRegion, i, targetMember);
        }
        for (PartitionRebalanceDetailsImpl details : detailSet) {
          String regionPath = details.getRegionPath();
          Long lrb = colocatedRegionBytes.get(regionPath);
          if (lrb != null) { // region could have gone away - esp during shutdow
            long regionBytes = lrb.longValue();
            // Only add the elapsed time to the leader region.
            details.incRemoves(regionBytes, 
                details.getRegion().equals(leaderRegion) ? elapsed : 0);
            totalBytes += regionBytes;
          }
        }
      } else {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} redundant bucket {} failed removal o{}", leaderRegion, i, targetMember);
        }
      }
    } finally {
      if (stats != null) {
        stats.endBucketRemove(regionCount, result, totalBytes, elapsed);
      }
    }

    return result;
  }

  @Override
  public boolean movePrimary(InternalDistributedMember source, 
      InternalDistributedMember target, int bucketId) {
    boolean result = false;
    long elapsed = 0;

    if (stats != null) {
      stats.startPrimaryTransfer(regionCount);
    }

    try {
      long start = System.nanoTime();
      result = delegate.movePrimary(source, target, bucketId);
      elapsed = System.nanoTime() - start;
      if (result) {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} primary bucket {} moved from {} to {}", leaderRegion, bucketId, source, target);
        }
        for (PartitionRebalanceDetailsImpl details : detailSet) {
          details.incPrimaryTransfers(details.getRegion().equals(leaderRegion) ? elapsed : 0);
        }
      } else {
        if (logger.isDebugEnabled()) {
          logger.debug("Rebalancing {} primary bucket {} failed to move from {} to {}", leaderRegion, bucketId, source, target);
        }
      }
    } finally {
      if (stats != null) {
        stats.endPrimaryTransfer(regionCount, result, elapsed);
      }
    }

    return result;
  }

  @Override
  public void waitForOperations() {
    delegate.waitForOperations();
  }

  public Set<PartitionRebalanceDetailsImpl> getDetailSet() {
    return this.detailSet;
  }
}
