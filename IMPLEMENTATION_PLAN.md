# TriviaCrave Backend Implementation Plan

## Project Overview

TriviaCrave is a mobile-first, real-time trivia game platform designed specifically for the Kenyan market, featuring culturally relevant content, social competition, and both public scheduled games and private user-hosted sessions.

## Engineering Principles (SOLID) and Architecture Guardrails

- [ ] Apply SOLID consistently across domains using layered architecture (resources → services → repositories → entities)
- [ ] Single Responsibility: Keep GraphQL resolvers thin; all business logic lives in services
- [ ] Open/Closed: Use strategy/factory patterns for pluggable algorithms (e.g., scoring, question selection)
- [ ] Liskov Substitution: Program to service interfaces; implementations are freely swappable in tests
- [ ] Interface Segregation: Split large services into focused, domain-specific interfaces
- [ ] Dependency Inversion: Resolvers and orchestrators depend on interfaces; implementations injected via constructors
- [ ] Ports & Adapters style: Define domain ports (interfaces) in domain modules; adapters (JPA, Redis, external APIs) implement them
- [ ] Enforce constructor injection only; avoid field injection
- [ ] No resolver accesses repositories directly; all data access goes through services

## Phase 1: Project Foundation & Core Setup

### 1. Spring Boot Project Structure
**Timeline: Days 1-2**

- [ ] Create Gradle build configuration with Spring Boot 3.3.5
- [ ] Add Netflix DGS 9.0.2 for GraphQL support
- [ ] Set up multi-module package structure following domain-driven design
- [ ] Configure Gradle wrapper and project settings
- [ ] Set up basic project structure with all domain packages

**Package Structure:**
```
src/main/java/com/mabawa/triviacrave/
├── TriviaCraveApplication.java
├── auth/            # Authentication & authorization
├── common/          # Shared configs, utils, exceptions
├── crew/            # Crew/team management
├── game/            # Game logic & orchestration
├── guest/           # Guest play features
├── question/        # Questions & categories
└── user/            # User management
```

**GraphQL Resolver Package Conventions (mirroring stocktake_app):**
```
src/main/java/com/mabawa/triviacrave/
├── user/
│   ├── resources/          # DGS resolvers/datafetchers (e.g., UserResource, UserDataFetcher)
│   ├── service/            # Interfaces (ports) + implementations
│   ├── repository/         # Spring Data repositories
│   ├── entity/             # JPA entities
│   └── mapper/             # Mappers between entities and generated GraphQL types
├── game/
│   ├── resources/ ...      # Same pattern for each domain
└── common/
    ├── scalars/            # TSID, LocalDate, LocalDateTime, Long
    ├── graphql/            # GraphQlExceptionHandler, DataLoader config, directives
    └── security/           # Annotations (e.g., AllowUnauthenticated)
```

### 2. Build Configuration & Dependencies
**Timeline: Day 2**

- [ ] Create `build.gradle.kts` with all required dependencies
- [ ] Configure Netflix DGS code generation
- [ ] Set up Spring Boot starters for JPA, Redis, WebSocket, Security
- [ ] Add database drivers (PostgreSQL) and migration tools (Flyway)
- [ ] Configure test dependencies

**Key Dependencies:**
```kotlin
dependencies {
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter:9.0.2")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    // Additional dependencies...
}
```

### 3. Application Configuration
**Timeline: Day 3**

- [ ] Create `application.yml` with development and production profiles
- [ ] Configure database connection settings
- [ ] Set up Redis connection properties
- [ ] Configure JWT settings and security properties
- [ ] Set up logging configuration
- [ ] Configure WebSocket settings

### 4. Database Schema Design
**Timeline: Days 4-6**

- [ ] Design comprehensive ER diagram
- [ ] Create Flyway migration scripts for all core entities
- [ ] Set up database relationships and constraints
- [ ] Configure JPA entities with proper annotations
- [ ] Add indexes for performance optimization

**Core Entities:**
- Users (authentication, profiles, statistics)
- Games (scheduled public games)
- Private Games (user-created games)
- Questions (question bank with categories)
- Crews (team structures)
- Game Participants (participation tracking)
- User Answers (answer history with timing)
- Game Invitations (invite codes and tracking)

## Phase 2: Core Domain Implementation

### 5. Common Infrastructure
**Timeline: Days 7-9**

- [ ] Create base entity classes with TSID generation
- [ ] Implement common exception classes and error handling
- [ ] Set up global exception handler with proper error responses
- [ ] Create utility classes for validation and conversion
- [ ] Implement custom GraphQL scalars (TSID, LocalDateTime, Long)
- [ ] Set up Redis configuration and cache services
- [ ] Add `GraphQlExceptionHandler` mapping domain and validation exceptions to GraphQL errors
- [ ] Introduce `@AllowUnauthenticated` (or equivalent) for public mutations/queries
- [ ] Configure DataLoader registry and per-request batching for N+1 prevention

### 6. Authentication & Security System
**Timeline: Days 10-14**

- [ ] Implement JWT service with token generation/validation
- [ ] Create user registration and login endpoints
- [ ] Set up Spring Security configuration
- [ ] Implement role-based access control (RBAC)
- [ ] Add support for multiple auth methods (email, phone OTP, Google OAuth)
- [ ] Create guest access system for private games
- [ ] Implement refresh token mechanism
- [ ] Define `AuthService` interface and `AuthServiceImpl`; resolvers depend on `AuthService`
- [ ] Apply `@PreAuthorize` on service methods; use `@AllowUnauthenticated` on public resolvers
- [ ] Use DIP: security components and resolvers depend on abstractions, not concrete classes

**Authentication Features:**
- Email/password registration and login
- Phone number OTP verification
- Google OAuth integration
- JWT token management with refresh tokens
- Guest user support
- Password reset functionality

### 7. User Domain Implementation
**Timeline: Days 15-18**

- [ ] Create User entity with all required fields
- [ ] Implement UserService with CRUD operations
- [ ] Add user profile management
- [ ] Create user statistics tracking
- [ ] Implement daily streak system
- [ ] Add user preferences and settings
- [ ] Create GraphQL datafetchers for user operations
- [ ] Extract `UserService` interface; implement `UserServiceImpl`; inject via constructor
- [ ] Keep resolvers thin: map GraphQL inputs/outputs to service calls only

### 8. Question & Category Management
**Timeline: Days 19-22**

- [ ] Create Question and Category entities
- [ ] Implement question CRUD operations
- [ ] Add category management system
- [ ] Create question randomization algorithms
- [ ] Implement difficulty level system
- [ ] Add bilingual support (English/Swahili)
- [ ] Create question analytics tracking
- [ ] Set up question validation and moderation

## Phase 3: GraphQL API Development

### 9. GraphQL Schema Design
**Timeline: Days 23-26**

- [ ] Design comprehensive GraphQL schemas for all domains
- [ ] Create input and output types for all operations
- [ ] Define queries, mutations, and subscriptions
- [ ] Set up schema stitching for modular design
- [ ] Configure DGS code generation
- [ ] Add custom directives for validation and authorization
 - [ ] Organize schemas per domain under `src/main/resources/schema/{domain}/*.graphql` (as in stocktake_app)

**Schema Modules:**
- `auth.graphql` - Authentication operations
- `user.graphql` - User management
- `game.graphql` - Game operations
- `private-game.graphql` - Private game specific operations
- `question.graphql` - Question and category management
- `crew.graphql` - Team/crew operations
- `leaderboard.graphql` - Scoring and rankings

### 10. DGS Datafetchers Implementation
**Timeline: Days 27-32**

- [ ] Create datafetchers for all query operations
- [ ] Implement mutation datafetchers with proper validation
- [ ] Set up subscription datafetchers for real-time features
- [ ] Add authentication and authorization to datafetchers
- [ ] Implement proper error handling and validation
- [ ] Create data loaders for efficient data fetching
- [ ] Add caching strategies for performance

**Conventions (mirroring stocktake_app):**
- Keep resolver classes in `{domain}/resources` and annotate with `@DgsComponent`
- Prefer `@DgsQuery`, `@DgsMutation`, `@DgsSubscription`; use `@DgsData(parentType=..., field=...)` where necessary
- Annotate public entry points with `@AllowUnauthenticated`; guard others with `@PreAuthorize`
- Use generated codegen types for inputs/outputs; map to domain models in services
- No repository calls in resolvers; resolvers delegate to service interfaces
- Register `DataLoader`s for batched fetches and attach to resolvers
- Surface errors via `GraphQlExceptionHandler` with consistent error codes/messages

Example resolver pattern:
```java
@DgsComponent
public class UserDataFetcher {
  private final UserService userService;

  public UserDataFetcher(UserService userService) { this.userService = userService; }

  @DgsMutation
  @AllowUnauthenticated
  public LoggedInUserResponse createUser(@InputArgument CreateUserCmd cmd) {
    return userService.createUser(cmd);
  }
}
```

### 11. Game Domain Core Logic
**Timeline: Days 33-38**

- [ ] Create Game and PrivateGame entities
- [ ] Implement game creation and configuration
- [ ] Add game state management
- [ ] Create game invitation system
- [ ] Implement game access control (codes, invitations)
- [ ] Add game participant management
- [ ] Create game lifecycle management (create, start, end)

## Phase 4: Real-Time Gaming Engine

### 12. WebSocket Infrastructure
**Timeline: Days 39-43**

- [ ] Configure STOMP protocol over WebSocket
- [ ] Implement WebSocket authentication and authorization
- [ ] Create message broadcasting system
- [ ] Add connection management and heartbeat
- [ ] Implement session synchronization
- [ ] Create WebSocket security filters
- [ ] Add connection cleanup and error handling

### 13. Live Game Orchestration
**Timeline: Days 44-50**

- [ ] Implement game session state management in Redis
- [ ] Create question sequencing and randomization
- [ ] Add timer synchronization across clients
- [ ] Implement real-time game flow (join → question → answer → results)
- [ ] Create game state transitions
- [ ] Add participant management during live games
- [ ] Implement game termination and cleanup

**Game Flow Implementation:**
1. **Join Phase**: Player registration and lobby management
2. **Question Phase**: Question delivery and timer management
3. **Answer Phase**: Answer collection and validation
4. **Results Phase**: Score calculation and leaderboard updates
5. **Countdown Phase**: Preparation for next question

### 14. Scoring & Leaderboard System
**Timeline: Days 51-56**

- [ ] Implement speed-based scoring algorithm
- [ ] Create real-time leaderboard updates using Redis sorted sets
- [ ] Add streak bonus calculations
- [ ] Implement crew-based scoring
- [ ] Create post-game statistics generation
- [ ] Add historical leaderboard tracking
- [ ] Implement rank change notifications
- [ ] Apply Open/Closed via strategy pattern for scoring; support swapping strategies without modifying callers

**Scoring Algorithm:**
- Base points: 100 for correct answer
- Speed multiplier: Decreases linearly over 15-second window
- Streak bonuses: Additional points for consecutive correct answers
- Crew scoring: Individual scores contribute to team totals

### 15. Anti-Cheat & Security Measures
**Timeline: Days 57-60**

- [ ] Implement minimum response time validation (1 second)
- [ ] Add pattern detection for suspicious behavior
- [ ] Create answer choice shuffling per user
- [ ] Implement question randomization strategies
- [ ] Add rate limiting on critical endpoints
- [ ] Create cheat detection algorithms
- [ ] Implement user reporting and moderation tools

## Phase 5: Social Features & Advanced Functionality

### 16. Crew System Implementation
**Timeline: Days 61-66**

- [ ] Create Crew and CrewMember entities
- [ ] Implement crew creation and management
- [ ] Add crew invitation system
- [ ] Create crew leaderboards and statistics
- [ ] Implement crew-based game modes
- [ ] Add crew chat functionality
- [ ] Create crew achievement system

### 17. Social Features
**Timeline: Days 67-72**

- [ ] Implement friend system and connections
- [ ] Create social sharing mechanisms
- [ ] Add WhatsApp integration for invites
- [ ] Implement achievement and badge system
- [ ] Create user activity feeds
- [ ] Add social notifications
- [ ] Implement user blocking and reporting

### 18. Notification System
**Timeline: Days 73-76**

- [ ] Create notification entities and management
- [ ] Implement email notification service
- [ ] Add push notification support
- [ ] Create SMS integration for OTP
- [ ] Implement notification preferences
- [ ] Add real-time in-app notifications
- [ ] Create notification templates and localization

## Phase 6: Advanced Game Features

### 19. Scheduled Public Games
**Timeline: Days 77-82**

- [ ] Implement cron-based game scheduling
- [ ] Create large-scale game orchestration (1000+ users)
- [ ] Add game lobby management for public games
- [ ] Implement waiting room functionality
- [ ] Create game announcement system
- [ ] Add spectator mode for ended games
- [ ] Implement game replay functionality

### 20. Performance & Scalability
**Timeline: Days 83-87**

- [ ] Optimize database queries and add proper indexing
- [ ] Implement Redis clustering for high availability
- [ ] Add connection pooling optimization
- [ ] Create horizontal scaling strategies
- [ ] Implement caching layers for frequent operations
- [ ] Add performance monitoring and metrics
- [ ] Optimize WebSocket connection handling

### 21. Content Management System
**Timeline: Days 88-92**

- [ ] Create admin panel for content moderation
- [ ] Implement question review and approval workflow
- [ ] Add bulk question import/export functionality
- [ ] Create category balancing algorithms
- [ ] Implement content scheduling system
- [ ] Add fact-checking integration
- [ ] Create content analytics dashboard

## Phase 7: Integration & DevOps

### 22. Local Development Environment
**Timeline: Days 93-95**

- [ ] Create `docker-compose.yml` for local development
- [ ] Set up PostgreSQL and Redis containers
- [ ] Configure development database seeding
- [ ] Add development-specific configurations
- [ ] Create startup scripts and documentation
- [ ] Set up hot reloading for development

### 23. Testing Strategy
**Timeline: Days 96-100**

- [ ] Create unit tests for all service classes
- [ ] Implement integration tests for GraphQL operations
- [ ] Add WebSocket integration tests
- [ ] Create performance tests for concurrent users
- [ ] Implement security tests for authentication
- [ ] Add database migration tests
- [ ] Create end-to-end test scenarios

### 24. Documentation & Deployment
**Timeline: Days 101-105**

- [ ] Create comprehensive API documentation
- [ ] Write deployment guides and configurations
- [ ] Set up CI/CD pipeline configuration
- [ ] Create Docker production images
- [ ] Add monitoring and logging configurations
- [ ] Create backup and disaster recovery procedures
- [ ] Write operational runbooks

## Technical Architecture Details

### Technology Stack
- **Backend Framework**: Spring Boot 3.3.5 with Java 17
- **GraphQL**: Netflix DGS 9.0.2
- **Database**: PostgreSQL 15+ with JPA/Hibernate
- **Caching**: Redis 7+ for sessions and game state
- **Real-time**: WebSocket with STOMP protocol
- **Security**: JWT with Spring Security
- **Migrations**: Flyway for database versioning
- **Build Tool**: Gradle with Kotlin DSL
- **Testing**: JUnit 5, Testcontainers, MockMVC

### Infrastructure Requirements
- **CPU**: Minimum 4 cores for production
- **Memory**: 8GB+ RAM for optimal performance
- **Database**: PostgreSQL with connection pooling
- **Cache**: Redis cluster for high availability
- **Storage**: SSD for database performance
- **Network**: High-speed internet for real-time features

### Security Considerations
- JWT token security with proper expiration
- HTTPS enforcement for all endpoints
- Input validation and sanitization
- Rate limiting on all public endpoints
- Database connection security
- Redis authentication and encryption
- CORS configuration for web clients
- Authentication audit logging

### Performance Targets
- **API Response Time**: < 100ms for 95% of requests
- **WebSocket Latency**: < 50ms for real-time updates
- **Concurrent Users**: Support 1000+ in single game session
- **Database Performance**: < 10ms query response time
- **Cache Hit Rate**: > 90% for frequent operations
- **System Uptime**: 99.9% availability target

## Success Metrics

### Technical Metrics
- [ ] All unit tests passing with >90% code coverage
- [ ] Integration tests covering all GraphQL operations
- [ ] Load testing supporting 1000+ concurrent users
- [ ] API response times under 100ms
- [ ] Zero critical security vulnerabilities
- [ ] Database migrations running without errors
- [ ] Real-time features with <50ms latency

### Functional Metrics
- [ ] User registration and authentication working
- [ ] Private game creation and joining functional
- [ ] Real-time game sessions operational
- [ ] Scoring and leaderboards accurate
- [ ] Crew system fully implemented
- [ ] Admin content management working
- [ ] All GraphQL queries/mutations/subscriptions operational

This implementation plan provides a comprehensive roadmap for building the TriviaCrave backend from scratch, with clear milestones, timelines, and technical specifications for each phase of development.