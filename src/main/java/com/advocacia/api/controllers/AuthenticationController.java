package com.advocacia.api.controllers;

import com.advocacia.api.domain.user.*;
import com.advocacia.api.infra.security.TokenService;
import com.advocacia.api.repositories.UserRepository;
import com.advocacia.api.services.IUserService;
import com.advocacia.api.domain.user.UserDTO;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("auth")
public class AuthenticationController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserRepository repository;
    @Autowired
    private TokenService tokenService;

    @Autowired
    private IUserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthenticationDTO data){
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.login(), data.password());
        var auth = this.authenticationManager.authenticate(usernamePassword);

        var token = tokenService.generateToken((User) auth.getPrincipal());

        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterDTO data){
        if(this.repository.findByLogin(data.login()) != null) return ResponseEntity.badRequest().build();

        String encryptedPassword = passwordEncoder.encode(data.password());
        User newUser = new User(data.name(), data.login(), encryptedPassword, data.role());

        this.repository.save(newUser);

        return ResponseEntity.ok().build();
    }

    @PutMapping("/updatepass")
    public ResponseEntity<Object> updatePassword(@RequestBody @Valid User updatedUser){
        User existingUser = (User) userService.findByLogin(updatedUser.getLogin());
        if (existingUser != null && existingUser.isEnabled()) {
            existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            userService.save(existingUser);
            return ResponseEntity.ok(existingUser);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado ou desativado");
        }
    }

    @GetMapping("/allusers")
    public ResponseEntity<Object> getAllUsers(){
        List<User> users = userService.findAll();
        List<UserDTO> userDTOs = new ArrayList<>();
        for(User user : users){
            UserDTO userDTO = new UserDTO(user.getId(), user.getName(), user.getLogin(), user.getRole());
            userDTOs.add(userDTO);
        }
        return ResponseEntity.status(HttpStatus.OK).body(userDTOs);
    }

    @GetMapping("/userid/{id}")
    public ResponseEntity<Object> findUserById(@PathVariable(value = "id") String id){
        Optional<User> userId = userService.findById(id);
        return userId.<ResponseEntity<Object>>map(user -> ResponseEntity.status(HttpStatus.OK).body(user)).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado!"));
    }

    @DeleteMapping("/deluser/{id}")
    public ResponseEntity<Object> deleteUserById(@PathVariable(value = "id") String id, Authentication auth){
        if (!auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autorizado!");
        }
        Optional<User> userId = userService.findById(id);
        if(userId.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado");
        }
        userService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Usuario deletado com sucesso!");
    }
}