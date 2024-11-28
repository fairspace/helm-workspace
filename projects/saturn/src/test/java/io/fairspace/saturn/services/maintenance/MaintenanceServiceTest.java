package io.fairspace.saturn.services.maintenance;

import org.apache.jena.query.Dataset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.ConflictException;
import io.fairspace.saturn.services.NotAvailableException;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.views.ViewService;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;

import static io.fairspace.saturn.TestUtils.loadViewsConfig;
import static io.fairspace.saturn.services.maintenance.MaintenanceService.MAINTENANCE_IS_IN_PROGRESS;
import static io.fairspace.saturn.services.maintenance.MaintenanceService.SERVICE_NOT_AVAILABLE;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MaintenanceServiceTest {

    private final UserService userService = mock(UserService.class);
    private final Dataset dataset = mock(Dataset.class);
    private final ViewStoreClientFactory viewStoreClientFactory = mock(ViewStoreClientFactory.class);
    private final ViewService viewService = mock(ViewService.class);
    private final ViewsProperties viewsProperties = loadViewsConfig("src/test/resources/test-views.yaml");
    private final MaintenanceService sut = spy(new MaintenanceService(
            viewsProperties, userService, dataset, viewStoreClientFactory, viewService, "localhost"));

    @Test
    public void testReindexingIsNotAllowedForNotAdmins() {
        // give
        var currentUser = new User();
        currentUser.setAdmin(false);
        when(userService.currentUser()).thenReturn(currentUser);

        // when/then
        assertThrows(AccessDeniedException.class, sut::startRecreateIndexTask);
    }

    @Test
    public void testReindexingIsNotAllowedWhenDisabled() {
        // give
        var currentUser = new User();
        currentUser.setAdmin(true);
        when(userService.currentUser()).thenReturn(currentUser);

        doReturn(true).when(sut).disabled();

        // when/then
        assertThrows(SERVICE_NOT_AVAILABLE, NotAvailableException.class, sut::startRecreateIndexTask);
    }

    @Test
    public void testReindexingIsNotAllowedWhenActive() {
        // give
        var currentUser = new User();
        currentUser.setAdmin(true);
        when(userService.currentUser()).thenReturn(currentUser);

        doReturn(true).when(sut).active();

        // when/then
        assertThrows(MAINTENANCE_IS_IN_PROGRESS, ConflictException.class, sut::startRecreateIndexTask);
    }

    @Test
    public void testReindexingIsExecutedAlongWithCachesRefresh() throws InterruptedException {
        // give
        var currentUser = new User();
        currentUser.setAdmin(true);
        when(userService.currentUser()).thenReturn(currentUser);

        doReturn(false).when(sut).active();
        doNothing().when(sut).recreateIndex();

        // when
        sut.startRecreateIndexTask();
        Thread.sleep(500);

        // then
        verify(sut).recreateIndex();
        verify(viewService).refreshCaches();
    }
}
