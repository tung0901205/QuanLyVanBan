package com.qlda.authservice.controller;

import com.qlda.authservice.common.ApiResponse;
import com.qlda.authservice.dto.directory.UserDirectoryResponse;
import com.qlda.authservice.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only authenticated directory used by business screens such as transfer,
 * delegation and signer selection. Administrative user mutations remain under
 * /api/auth/users and are restricted to ADMIN at the gateway.
 */
@RestController
@RequestMapping("/api/auth/directory")
public class UserDirectoryController {

    private final UserService userService;

    public UserDirectoryController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDirectoryResponse>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer donViId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success("Get user directory successfully",
                userService.getDirectoryUsers(role, donViId, keyword));
    }
}
