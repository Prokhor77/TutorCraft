package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-COURSE-03: один уровень подмодулей. */
class ModuleHierarchyTest {

    private static CourseModule module(UUID parentId) {
        return new CourseModule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), parentId, "M", 0,
                Visibility.PUBLISHED, null, null, 0, null);
    }

    @Test
    void topLevelAndOneSublevelAreAllowed() {
        CourseModule top = module(null);
        assertThatCode(() -> ModuleHierarchy.requireValidParent(null, null, false)).doesNotThrowAnyException();
        assertThatCode(() -> ModuleHierarchy.requireValidParent(null, top, false)).doesNotThrowAnyException();
        assertThatCode(() -> ModuleHierarchy.requireValidParent(UUID.randomUUID(), top, false)).doesNotThrowAnyException();
    }

    @Test
    void secondSublevelIsRejected() {
        CourseModule nested = module(UUID.randomUUID());
        assertThatThrownBy(() -> ModuleHierarchy.requireValidParent(null, nested, false))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo(ModuleHierarchy.DEPTH_EXCEEDED));
    }

    @Test
    void moduleWithChildrenCannotBecomeNested() {
        assertThatThrownBy(() -> ModuleHierarchy.requireValidParent(UUID.randomUUID(), module(null), true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void moduleCannotBeItsOwnParent() {
        CourseModule top = module(null);
        assertThatThrownBy(() -> ModuleHierarchy.requireValidParent(top.id(), top, false))
                .isInstanceOf(BusinessRuleException.class);
    }
}
