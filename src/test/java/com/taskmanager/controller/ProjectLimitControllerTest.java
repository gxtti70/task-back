package com.taskmanager.controller;

import com.taskmanager.dto.request.ProjectRequest;
import com.taskmanager.entity.User;
import com.taskmanager.repository.*;
import com.taskmanager.service.NotificationEventService;
import com.taskmanager.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProjectLimitControllerTest {
    @Test void returns402BeforeSavingWhenThePlanProjectLimitIsReached() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@example.test").fullName("Owner").build();
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        var controller = new ProjectController(projects, mock(TaskRepository.class), mock(ProjectMemberRepository.class),
                users, mock(NotificationEventService.class), subscriptions);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(owner));
        doThrow(new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Plan limit reached"))
                .when(subscriptions).checkCanCreateProject(ownerId);
        var auth = UsernamePasswordAuthenticationToken.authenticated("owner@example.test", "",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
        ProjectRequest request = new ProjectRequest(); request.setName("Over limit"); request.setStatus("ACTIVE");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.createProject(request, auth));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
        verify(projects, never()).save(any());
    }
}
