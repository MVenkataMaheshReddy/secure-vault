package com.securevault.service;

import com.securevault.entity.Document;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentAccessServiceTest {

    private DocumentAccessService accessService;

    private User owner;
    private User otherUser;
    private User adminUser;
    private Document document;

    @BeforeEach
    void setUp() {
        accessService = new DocumentAccessService();

        Role employeeRole = new Role(RoleName.EMPLOYEE);
        Role adminRole = new Role(RoleName.ADMIN);

        owner = new User("Alice", "alice@example.com", "password");
        owner.setId(1L);
        owner.setRoles(Collections.singleton(employeeRole));

        otherUser = new User("Bob", "bob@example.com", "password");
        otherUser.setId(2L);
        otherUser.setRoles(Collections.singleton(employeeRole));

        adminUser = new User("SuperAdmin", "admin@example.com", "password");
        adminUser.setId(99L);
        adminUser.setRoles(Collections.singleton(adminRole));

        document = new Document("My Passport", "passport.pdf", "ID", "Alice's passport", owner);
        document.setId(10L);
    }

    @Test
    void testOwnerCanViewOwnDocument() {
        assertTrue(accessService.canView(document, "alice@example.com", false));
        assertTrue(accessService.canView(document, owner));
    }

    @Test
    void testAdminCanViewAnyDocument() {
        assertTrue(accessService.canView(document, "admin@example.com", true));
        assertTrue(accessService.canView(document, adminUser));
    }

    @Test
    void testNonOwnerCannotViewDocument() {
        assertFalse(accessService.canView(document, "bob@example.com", false));
        assertFalse(accessService.canView(document, otherUser));
    }

    @Test
    void testOwnerCanDownloadOwnDocument() {
        assertTrue(accessService.canDownload(document, "alice@example.com", false));
    }

    @Test
    void testNonOwnerCannotDownloadDocument() {
        assertFalse(accessService.canDownload(document, "bob@example.com", false));
    }

    @Test
    void testAdminCanDownloadAnyDocument() {
        assertTrue(accessService.canDownload(document, "admin@example.com", true));
    }

    @Test
    void testOwnerCanEditOwnDocument() {
        assertTrue(accessService.canEdit(document, "alice@example.com", false));
        assertTrue(accessService.canEdit(document, owner));
    }

    @Test
    void testNonOwnerCannotEditDocument() {
        assertFalse(accessService.canEdit(document, "bob@example.com", false));
        assertFalse(accessService.canEdit(document, otherUser));
    }

    @Test
    void testAdminCanEditAnyDocument() {
        assertTrue(accessService.canEdit(document, "admin@example.com", true));
        assertTrue(accessService.canEdit(document, adminUser));
    }

    @Test
    void testOwnerCanDeleteOwnDocument() {
        assertTrue(accessService.canDelete(document, "alice@example.com", false));
        assertTrue(accessService.canDelete(document, owner));
    }

    @Test
    void testNonOwnerCannotDeleteDocument() {
        assertFalse(accessService.canDelete(document, "bob@example.com", false));
        assertFalse(accessService.canDelete(document, otherUser));
    }

    @Test
    void testAdminCanDeleteAnyDocument() {
        assertTrue(accessService.canDelete(document, "admin@example.com", true));
        assertTrue(accessService.canDelete(document, adminUser));
    }

    @Test
    void testOwnerCanRestoreOwnDocument() {
        assertTrue(accessService.canRestore(document, "alice@example.com", false));
    }

    @Test
    void testNonOwnerCannotRestoreDocument() {
        assertFalse(accessService.canRestore(document, "bob@example.com", false));
    }

    @Test
    void testAdminCanRestoreAnyDocument() {
        assertTrue(accessService.canRestore(document, "admin@example.com", true));
    }

    @Test
    void testNullChecksReturnFalseSafely() {
        assertFalse(accessService.canView(null, "alice@example.com", false));
        assertFalse(accessService.canView(document, (String) null, false));
        assertFalse(accessService.canView(null, (User) null));
    }
}
