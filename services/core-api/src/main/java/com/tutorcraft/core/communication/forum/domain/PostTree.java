package com.tutorcraft.core.communication.forum.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/** Сборка дерева постов из плоского списка (порядок детей — порядок входного списка). */
public final class PostTree {

    private PostTree() {
    }

    /**
     * @param toNode фабрика узла: пост + уже собранные дети
     * @return корни (посты без видимого родителя)
     */
    public static <N> List<N> build(List<ForumPost> posts, BiFunction<ForumPost, List<N>, N> toNode) {
        Map<UUID, List<ForumPost>> children = new LinkedHashMap<>();
        Map<UUID, ForumPost> byId = new LinkedHashMap<>();
        posts.forEach(post -> byId.put(post.id(), post));
        List<ForumPost> roots = new ArrayList<>();
        for (ForumPost post : posts) {
            if (post.parentId() != null && byId.containsKey(post.parentId())) {
                children.computeIfAbsent(post.parentId(), id -> new ArrayList<>()).add(post);
            } else {
                roots.add(post);
            }
        }
        return roots.stream().map(root -> node(root, children, toNode, 0)).toList();
    }

    private static <N> N node(ForumPost post, Map<UUID, List<ForumPost>> children, BiFunction<ForumPost, List<N>, N> toNode,
                              int level) {
        List<N> kids = level > ReplyPlacement.MAX_DEPTH ? List.of() : children.getOrDefault(post.id(), List.of()).stream()
                .map(child -> node(child, children, toNode, level + 1)).toList();
        return toNode.apply(post, kids);
    }
}
