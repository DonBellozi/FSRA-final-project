package ru.mephi.fsra.booking.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.booking.entity.User;
import ru.mephi.fsra.booking.entity.UserRole;
import ru.mephi.fsra.booking.repository.UserRepository;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User register(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.length() < 8) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Username is required and password must contain at least 8 characters");
    }

    if (userRepository.existsByUsername(username)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }

    User user = new User();
    user.setUsername(username);
    user.setPassword(passwordEncoder.encode(password));
    user.setRole(UserRole.USER);

    return userRepository.save(user);
  }

  @org.springframework.transaction.annotation.Transactional
  public User update(Long id, String username, String password, UserRole role) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (username != null) {
      if (username.isBlank())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username required");
      user.setUsername(username);
    }
    if (password != null) {
      if (password.length() < 8)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too short");
      user.setPassword(passwordEncoder.encode(password));
    }
    if (role != null) user.setRole(role);
    return userRepository.save(user);
  }

  @org.springframework.transaction.annotation.Transactional
  public void delete(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    userRepository.delete(user);
  }

  public List<User> findAll() {
    return userRepository.findAll();
  }

  public User authenticate(String username, String password) {
    if (username == null || password == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid username or password"));

    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    return user;
  }
}
