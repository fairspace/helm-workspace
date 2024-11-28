package io.fairspace.saturn.config.properties;

import java.util.List;

import lombok.Data;
import org.apache.jena.dboe.base.block.FileMode;
import org.apache.jena.tdb2.params.StoreParams;
import org.apache.jena.tdb2.params.StoreParamsBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.jena.store-params")
public class StoreParamsProperties {

    private FileMode fileMode;
    private Integer blockSize;
    private Integer blockReadCacheSize;
    private Integer blockWriteCacheSize;
    private Integer node2nodeidCacheSize;
    private Integer nodeid2nodeCacheSize;
    private Integer nodeMissCacheSize;
    private String nodeTable;
    private String tripleIndexPrimary;
    private List<String> tripleIndexes;
    private String quadIndexPrimary;
    private List<String> quadIndexes;
    private String prefixTable;
    private String prefixIndexPrimary;
    private List<String> prefixIndexes;

    public StoreParams buildStoreParams() {
        return isStoreParamsSet() ? createStoreParams() : StoreParams.getDftStoreParams();
    }

    private StoreParams createStoreParams() {
        return StoreParamsBuilder.create()
                .fileMode(fileMode)
                .blockSize(blockSize)
                .blockReadCacheSize(blockReadCacheSize)
                .blockWriteCacheSize(blockWriteCacheSize)
                .node2NodeIdCacheSize(node2nodeidCacheSize)
                .nodeId2NodeCacheSize(nodeid2nodeCacheSize)
                .nodeMissCacheSize(nodeMissCacheSize)
                .nodeTableBaseName(nodeTable)
                .primaryIndexTriples(tripleIndexPrimary)
                .tripleIndexes(tripleIndexes.toArray(new String[0]))
                .primaryIndexQuads(quadIndexPrimary)
                .quadIndexes(quadIndexes.toArray(new String[0]))
                .prefixTableBaseName(prefixTable)
                .primaryIndexPrefix(prefixIndexPrimary)
                .prefixIndexes(prefixIndexes.toArray(new String[0]))
                .build();
    }

    private boolean isStoreParamsSet() {
        return fileMode != null
                || blockSize != null
                || blockReadCacheSize != null
                || blockWriteCacheSize != null
                || node2nodeidCacheSize != null
                || nodeid2nodeCacheSize != null
                || nodeMissCacheSize != null
                || nodeTable != null
                || tripleIndexPrimary != null
                || tripleIndexes != null
                || quadIndexPrimary != null
                || quadIndexes != null
                || prefixTable != null
                || prefixIndexPrimary != null
                || prefixIndexes != null;
    }
}
