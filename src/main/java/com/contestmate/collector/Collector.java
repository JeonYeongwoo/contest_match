package com.contestmate.collector;

import java.util.List;

/**
 * Implement one of these per source type/site. Only allowed, robots.txt-permitting sources
 * should ever be registered as a Source in the first place (see RobotsTxtChecker).
 */
public interface Collector {
    boolean supports(SourceType type);

    List<CollectedItem> collect(Source source) throws Exception;
}
