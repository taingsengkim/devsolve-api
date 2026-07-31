package kh.edu.istad.ite.devsoleapi.feature.notification;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRepositoryQueryTest {

    @Test
    void followerDispatchUsesDeclaredTableAlias() throws Exception {
        Method method = NotificationRepository.class.getMethod(
                "dispatchToFollowers",
                String.class,
                UUID.class,
                UUID.class,
                String.class,
                String.class,
                String.class,
                UUID.class,
                String.class
        );
        String sql = method.getAnnotation(Query.class).value();
        Modifying modifying = method.getAnnotation(Modifying.class);

        assertTrue(sql.contains("FROM public.follows follow_record"));
        assertTrue(sql.contains("follow_record.follower_id"));
        assertFalse(sql.contains("follow.follower_id"));
        assertFalse(modifying.clearAutomatically());
        assertTrue(modifying.flushAutomatically());
    }
}
