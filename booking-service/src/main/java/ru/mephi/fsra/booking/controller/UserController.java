package ru.mephi.fsra.booking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.booking.entity.User;
import ru.mephi.fsra.booking.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final ru.mephi.fsra.booking.service.TokenService tokens;

  public UserController(
      UserService userService, ru.mephi.fsra.booking.service.TokenService tokens) {
    this.userService = userService;
    this.tokens = tokens;
  }

  public record AdminUserRequest(
      String username, String password, ru.mephi.fsra.booking.entity.UserRole role) {}

  private UserResponse response(User user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getRole().name());
  }

  @GetMapping
  public java.util.List<UserResponse> users() {
    return userService.findAll().stream().map(this::response).toList();
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public UserResponse create(@RequestBody AdminUserRequest request) {
    User user = userService.register(request.username(), request.password());
    return response(userService.update(user.getId(), null, null, request.role()));
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}")
  public UserResponse update(@PathVariable("id") Long id, @RequestBody AdminUserRequest request) {
    return response(userService.update(id, request.username(), request.password(), request.role()));
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("id") Long id) {
    userService.delete(id);
  }

  public record RegisterRequest(String username, String password) {}

  public record UserResponse(Long id, String username, String role) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ru.mephi.fsra.booking.service.TokenService.TokenResponse register(
      @RequestBody RegisterRequest request) {
    User user = userService.register(request.username(), request.password());

    return tokens.issue(user);
  }

  public record AuthRequest(String username, String password) {}

  @PostMapping("/auth")
  public ru.mephi.fsra.booking.service.TokenService.TokenResponse authenticate(
      @RequestBody AuthRequest request) {
    User user = userService.authenticate(request.username(), request.password());

    return tokens.issue(user);
  }
}
