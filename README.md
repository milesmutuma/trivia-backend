# TriviaCrave 🧠🎯
**Live Trivia Gaming Platform for Kenyan Users**

TriviaCrave is a mobile-first, real-time trivia game platform designed specifically for the Kenyan market, featuring culturally relevant content, social competition, and both public scheduled games and private user-hosted sessions.

## 🌟 Key Features

### 🎮 Live Trivia Games
- **Scheduled Public Games**: Daily trivia sessions at fixed times (e.g., 7 PM EAT) with 1000+ concurrent players
- **Private Game Sessions**: User-created, invite-only games for friends, family, and colleagues (up to 50 participants)
- **Real-time Competition**: Speed-based scoring system where faster correct answers earn more points
- **Live Leaderboards**: Rankings update after each question for immediate competitive feedback

### 🌍 Culturally Relevant Content
- **Kenyan-Focused Categories**: Local music, sports, current affairs, Swahili language, geography
- **AI-Generated Questions**: GPT-powered content generation with human curation for cultural accuracy
- **Bilingual Support**: Questions and interface available in English and Swahili
- **Local Context**: Questions about Kenyan celebrities, landmarks, history, and current events

### 👥 Social Features
- **Crew System**: Form teams with friends for ongoing competition across multiple games
- **Crew Leaderboards**: Team-based rankings that persist across game sessions
- **WhatsApp Integration**: Share game invites and results via Kenya's most popular messaging platform
- **Daily Streaks**: Track consecutive days played with rewards and badges

### 📱 Mobile-Optimized Experience
- **Low-Bandwidth Design**: Optimized for 2G/3G networks common in Kenya
- **Progressive Web App**: Works seamlessly across Android and iOS devices
- **Guest Access**: Join private games without registration for reduced friction
- **Offline Capabilities**: Practice modes and cached content for poor connectivity

## 🏗️ Technical Architecture

### Backend Stack
- **Java 17** with **Spring Boot 3.2+**
- **Netflix DGS** for GraphQL API
- **PostgreSQL 15+** for primary data storage
- **Redis 7+** for caching, sessions, and real-time game state
- **WebSocket** for live game communication

### AI Content Service
- **Python 3.11+** with **FastAPI**
- **OpenAI GPT-3.5/GPT-4** integration for question generation
- **Automated fact-checking** and content quality assurance
- **Cultural relevance validation** pipeline

### Mobile Application
- **Flutter 3.16+** with **Dart 3.2+**
- **BLoC pattern** for state management
- **WebSocket** integration for real-time features
- **SQLite** for offline storage and caching

### 🗂️ Project Structure

The backend follows a conventional Spring Boot + Gradle layout (mirroring the style used in `stocktake_app`).

```text
trivia_crave_backend/
├── build.gradle.kts                 # Gradle build configuration
├── settings.gradle.kts              # Gradle project settings
├── gradle/
│   └── wrapper/                     # Gradle wrapper files
├── gradlew                          # Gradle wrapper (Unix)
├── gradlew.bat                      # Gradle wrapper (Windows)
├── docker-compose.yml               # Local development services (Postgres, Redis)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/mabawa/triviacrave/
│   │   │       ├── TriviaCraveApplication.java
│   │   │       ├── auth/            # Auth controllers, DTOs, services
│   │   │       ├── common/          # Config, exceptions, utils, security
│   │   │       ├── crew/            # Crew domain (entities, repos, services)
│   │   │       ├── game/            # Game domain (entities, orchestration, scoring)
│   │   │       ├── guest/           # Guest play domain
│   │   │       ├── question/        # Questions, categories, analytics
│   │   │       └── user/            # User domain and streaks
│   │   └── resources/
│   │       ├── application.yml      # Spring configuration
│   │       ├── db/
│   │       │   └── migration/       # Flyway migrations (V1__...sql)
│   │       └── schema/              # GraphQL schema files (*.graphql)
│   └── test/
│       └── java/                    # Unit and integration tests
└── README.md                        # Project overview and docs
```

- **src/main/java/com/mabawa/triviacrave**: Organized by domain (auth, game, question, user, crew, guest) with clear separation of `controller`/`datafetcher`, `service`, `repository`, `entity`, and `dto` packages.
- **src/main/resources/db/migration**: Flyway versioned SQL migrations for database schema.
- **src/main/resources/schema**: GraphQL schema split by domain (`auth`, `content`, `game`, `shared`, etc.).
- **common/**: Cross-cutting concerns: configuration, exception handling, security, utilities, and WebSocket messaging.

> Note: If you’ve just checked out the repo and don’t see these folders locally, run a full checkout or restore deleted files from Git history; the structure above reflects the intended layout.

## ⚡ GraphQL API (DGS) with Codegen Types

- **No REST controllers for GraphQL**: Use DGS resolvers/datafetchers (`@DgsQuery`, `@DgsMutation`, `@DgsSubscription`). Keep REST only for health checks, webhooks, file uploads, OAuth callbacks, actuator.
- **No custom DTOs**: Use generated GraphQL types (inputs/outputs) instead of hand-written DTO classes. Map between domain entities and generated types in services/resolvers.

### Setup (Gradle + DGS Codegen)

Add the DGS starter and codegen plugin to `build.gradle.kts`:

```kotlin
plugins {
  id("org.springframework.boot") version "3.3.5"
  id("io.spring.dependency-management") version "1.1.6"
  id("java")
  id("com.netflix.dgs.codegen") version "6.0.5"
}

dependencies {
  implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter:9.0.2")
  // JPA, DB, Redis, etc.
}

dgsCodegen {
  schemaPaths = listOf("src/main/resources/schema")
  packageName = "com.mabawa.triviacrave.generated"
  typeMapping = mapOf(
    "ID" to "java.lang.String",
      "Long" to "java.lang.Long",
    "DateTime" to "java.time.OffsetDateTime",
    "LocalDateTime" to "java.time.LocalDateTime",
      "TSID" to "java.lang.Long"
  )
  generateClient = false
}
```

Schemas live in `src/main/resources/schema/**/*.graphql`. Generate types with:

```bash
./gradlew generateJava
```

### Using generated types in resolvers

```java
@DgsComponent
public class UserDataFetcher {
  private final UserService userService;

  public UserDataFetcher(UserService userService) { this.userService = userService; }

  @DgsQuery
  public com.mabawa.triviacrave.generated.types.User user(@InputArgument String id) {
    UserEntity entity = userService.findById(id);
    return com.mabawa.triviacrave.generated.types.User.newBuilder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .build();
  }

  @DgsMutation
  public com.mabawa.triviacrave.generated.types.User createUser(
      @InputArgument com.mabawa.triviacrave.generated.types.CreateUserInput input) {
    UserEntity saved = userService.create(input.getName(), input.getEmail());
    return com.mabawa.triviacrave.generated.types.User.newBuilder()
        .id(saved.getId())
        .name(saved.getName())
        .email(saved.getEmail())
        .build();
  }
}
```

### Validation

- Validate inputs at resolver/service boundaries (manual checks or a validation service). Generated input classes don’t carry validation annotations by default.

### Net effect

- Replace controllers with DGS resolvers for GraphQL.
- Use codegen types as inputs/outputs; map to domain entities.

## 🎯 Core Game Mechanics

### Live Game Flow
1. **Join Phase**: Players join scheduled public games or private game lobbies
2. **Question Phase**: 30-40 multiple choice questions per 15-minute session
3. **Answer Window**: 15 seconds to submit answers
4. **Results Phase**: 5 seconds showing correct answer and leaderboard updates
5. **Countdown**: 5 seconds before next question begins

### Scoring System
- **Speed-Based Points**: Start at 100 points, decrease over the 15-second window
- **Faster = More Points**: Answer in 1 second = ~100 points, answer in 14 seconds = ~10 points
- **Zero for Incorrect**: Wrong answers earn 0 points regardless of speed
- **Anti-Cheat Design**: Prevents ties and discourages collaboration

### Private Game Features
- **Custom Game Creation**: Choose categories, difficulty, duration
- **Host Controls**: Start, pause, skip questions, manage participants
- **Invite System**: Shareable links with unique codes (triviacrave.com/join/ABC123)
- **Guest Participation**: Non-registered users can join via invite links
- **WhatsApp Sharing**: Pre-filled invitation messages for easy sharing

## 🚀 Implementation Plan

### Phase 1: Core Backend (Weeks 1-4)
**Week 1-2: Foundation**
- [ ] Spring Boot project setup with Netflix DGS
- [ ] Database schema design and entity modeling
- [ ] User authentication system (email, phone OTP, Google OAuth)
- [ ] Basic GraphQL schema and resolvers
- [ ] Redis integration for caching

**Week 3-4: Game Management**
- [ ] Question management system (CRUD operations)
- [ ] Private game creation and configuration
- [ ] Invite link generation and validation
- [ ] Basic game orchestration logic
- [ ] WebSocket connection handling

### Phase 2: Real-Time Gaming (Weeks 5-8)
**Week 5-6: Live Game Engine**
- [ ] WebSocket-based real-time communication
- [ ] Game state management in Redis
- [ ] Question sequencing and randomization
- [ ] Answer submission and validation
- [ ] Timer synchronization across clients

**Week 7-8: Leaderboards & Scoring**
- [ ] Speed-based scoring algorithm
- [ ] Real-time leaderboard updates
- [ ] Redis sorted sets for efficient ranking
- [ ] Post-game statistics and results
- [ ] Anti-cheat measures implementation

### Phase 3: Social Features (Weeks 9-12)
**Week 9-10: Crew System**
- [ ] Crew creation and membership management
- [ ] Crew leaderboards and statistics
- [ ] Crew invitation system
- [ ] Team-based scoring calculations
- [ ] Crew chat functionality

**Week 11-12: Enhanced Social**
- [ ] Daily streak tracking
- [ ] Achievement and badge system
- [ ] WhatsApp integration for sharing
- [ ] Friend system and social connections
- [ ] Notification system (push, SMS, email)

### Phase 4: Content & AI Integration (Weeks 13-16)
**Week 13-14: AI Service**
- [ ] Python FastAPI service setup
- [ ] OpenAI GPT integration
- [ ] Question generation pipeline
- [ ] Content quality assurance workflow
- [ ] Cultural relevance validation

**Week 15-16: Content Management**
- [ ] Admin dashboard for content moderation
- [ ] Question review and approval system
- [ ] Category management and balancing
- [ ] Automated content scheduling
- [ ] Fact-checking integration

### Phase 5: Mobile Application (Weeks 17-24)
**Week 17-20: Core Mobile App**
- [ ] Flutter project setup and architecture
- [ ] GraphQL client integration
- [ ] Authentication flows (email, phone, OAuth)
- [ ] Home dashboard with game schedules
- [ ] Private game creation interface

**Week 21-24: Real-Time Mobile Features**
- [ ] WebSocket integration for live games
- [ ] Real-time game interface
- [ ] Leaderboard displays and animations
- [ ] Crew management screens
- [ ] Offline capabilities and caching

### Phase 6: Advanced Features & Polish (Weeks 25-28)
**Week 25-26: Public Games**
- [ ] Scheduled public game system
- [ ] Large-scale game orchestration (1000+ users)
- [ ] Advanced anti-cheat measures
- [ ] Performance optimization
- [ ] Load balancing and scaling

**Week 27-28: Launch Preparation**
- [ ] Security audit and penetration testing
- [ ] Performance testing and optimization
- [ ] Mobile app store submission
- [ ] Beta testing with Kenyan users
- [ ] Documentation and deployment guides

## 📊 Database Schema Overview

### Core Entities
- **Users**: Authentication, profiles, statistics, preferences
- **Games**: Scheduled public games and private game sessions  
- **Private Games**: User-created games with custom settings
- **Questions**: Question bank with categories and difficulty levels
- **Crews**: Team structures with membership management
- **Game Participants**: Participation tracking for all game types
- **User Answers**: Individual answer history with timing data
- **Game Invitations**: Private game invite codes and tracking

### Key Relationships
- Users ↔ Crews (Many-to-Many): Through CrewMember entity
- Users ↔ Games (Many-to-Many): Through GameParticipant entity
- Users ↔ Private Games (One-to-Many): Users can host multiple games
- Questions ↔ Categories (Many-to-One): Organized question sets
- Private Games ↔ Game Invitations (One-to-Many): Multiple invite codes

## 🔐 Security Features

### Authentication & Authorization
- **Multi-factor authentication** (email, phone OTP, OAuth)
- **JWT-based security** with refresh token rotation
- **Role-based access control** (User, Host, Admin, Moderator)
- **Guest access** for private games with limited permissions

### Anti-Cheat Measures
- **Answer timing validation** (minimum 1 second response time)
- **Question randomization** per user to prevent sharing
- **Answer choice shuffling** to prevent position-based cheating
- **Pattern detection** for suspicious behavior
- **Rate limiting** on all critical endpoints

### Data Protection
- **GDPR compliance** with right to be forgotten
- **Kenya Data Protection Act** adherence
- **Encryption at rest** for sensitive data
- **TLS 1.3** for all data transmission

## 🌐 Deployment Architecture

### Production Environment
- **Containerized services** using Docker
- **Kubernetes orchestration** for scalability
- **Cloud deployment** (AWS/DigitalOcean)
- **CloudFlare CDN** for asset delivery and DDoS protection
- **Redis Cluster** for high-availability caching

### Monitoring & Operations
- **Application performance monitoring** (APM)
- **Error tracking** and alerting
- **Real-time dashboards** for game metrics
- **Automated backups** and disaster recovery
- **CI/CD pipeline** for seamless deployments

## 📱 Mobile App Features

### User Experience
- **Frictionless onboarding** with demo mode
- **Real-time notifications** for game reminders
- **Offline practice mode** for poor connectivity
- **WhatsApp integration** for viral sharing
- **Localization** (English and Swahili support)

### Technical Features
- **Progressive Web App** capabilities
- **Offline-first architecture** with smart syncing
- **Efficient caching** for low-bandwidth environments
- **Battery optimization** for extended play sessions
- **Cross-platform compatibility** (iOS and Android)

## 🎯 Success Metrics

### User Engagement
- **Daily Active Users** (target: 40% of registered users)
- **Session Duration** (target: 15+ minutes during live games)
- **Retention Rates** (target: 30%+ day-7 retention)
- **Game Completion Rate** (target: 80%+ finish entire sessions)

### Social Features
- **Crew Participation** (target: 60% of users join crews)
- **Private Game Creation** (target: 20% of users host games)
- **Viral Coefficient** (target: 1.2 invites per active user)
- **WhatsApp Sharing** (target: 40% share results/invites)

### Content Quality
- **Question Accuracy** (target: 95%+ factually correct)
- **Cultural Relevance** (target: 90%+ locally appropriate)
- **User Satisfaction** (target: 4+ stars average rating)

## 🤝 Contributing

We welcome contributions to make TriviaCrave the best trivia platform for Kenya! Please read our contributing guidelines and code of conduct.

### Development Setup
1. Clone the repository
2. Set up local PostgreSQL and Redis instances
3. Configure environment variables
4. Run `./gradlew bootRun` for backend
5. Run `flutter run` for mobile app

### Testing
- Unit tests: `./gradlew test`
- Integration tests: `./gradlew integrationTest`
- Mobile tests: `flutter test`

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Contact

- **Project Lead**: [Your Name]
- **Email**: contact@triviacrave.com
- **WhatsApp Business**: +254-XXX-XXXX
- **Website**: https://triviacrave.com

---

**Join the trivia revolution in Kenya! 🇰🇪**