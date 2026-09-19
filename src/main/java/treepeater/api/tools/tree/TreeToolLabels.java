package treepeater.api.tools.tree;

import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.api.Json;

final class TreeToolLabels {

    private TreeToolLabels() {}

    static String nodeIdSuffix(JsonNode args) {
        OptionalInt nodeId = Json.integer(args, "node_id", "nodeId", "id");
        return nodeId.isPresent() ? " · id " + nodeId.getAsInt() : "";
    }
}
