# Solana Tip Backend

Backend API pour la plateforme de tipping de créateurs de contenu en Solana.

## Stack technique

- **Java 21** + **Spring Boot 3.2**
- **Spring Data JPA** (H2 en dev, PostgreSQL en prod)
- **WebFlux WebClient** pour les appels RPC Solana
- **Lombok** pour réduire le boilerplate

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Frontend React                  │
│         (Phantom Wallet + Tailwind CSS)          │
└──────────────┬──────────────────┬───────────────┘
               │ REST API         │ SOL Transfer
               ▼                  ▼
┌──────────────────────┐  ┌──────────────────────┐
│   Spring Boot API    │  │   Solana Blockchain   │
│                      │  │                       │
│  ┌────────────────┐  │  │  Native SOL Transfer  │
│  │  Controllers   │  │  │  (System Program)     │
│  ├────────────────┤  │  └──────────┬────────────┘
│  │   Services     │──┼─────────────┘
│  ├────────────────┤  │  Verify tx via RPC
│  │  Repositories  │  │
│  ├────────────────┤  │
│  │   Database     │  │
│  └────────────────┘  │
└──────────────────────┘
```

## Flow d'un tip

1. L'utilisateur connecte son Phantom Wallet
2. Il choisit un créateur et un montant
3. Le frontend envoie la transaction SOL via Phantom
4. Phantom retourne la signature de la transaction
5. Le frontend envoie la signature au backend (`POST /api/v1/tips`)
6. Le backend vérifie la transaction on-chain via Solana RPC
7. Si confirmée, le tip est enregistré avec le statut `CONFIRMED`

## API Endpoints

### Creators

| Méthode | Endpoint                   | Description              |
|---------|----------------------------|--------------------------|
| POST    | `/api/v1/creators`         | Créer un profil créateur |
| GET     | `/api/v1/creators`         | Lister tous les créateurs|
| GET     | `/api/v1/creators/{username}` | Profil d'un créateur  |
| PUT     | `/api/v1/creators/{username}` | Modifier un profil    |
| DELETE  | `/api/v1/creators/{username}` | Supprimer un profil   |

### Tips

| Méthode | Endpoint                              | Description                     |
|---------|---------------------------------------|---------------------------------|
| POST    | `/api/v1/tips`                        | Enregistrer un tip              |
| GET     | `/api/v1/tips/creator/{username}`     | Historique des tips d'un créateur|
| GET     | `/api/v1/tips/sender/{walletAddress}` | Historique des tips envoyés     |

## Lancer en dev

```bash
./mvnw spring-boot:run
```

L'API sera disponible sur `http://localhost:8080`.
La console H2 est accessible sur `http://localhost:8080/h2-console`.

## Variables d'environnement (prod)

| Variable              | Description                    |
|-----------------------|--------------------------------|
| `DATABASE_URL`        | URL PostgreSQL                 |
| `DATABASE_USERNAME`   | User PostgreSQL                |
| `DATABASE_PASSWORD`   | Password PostgreSQL            |
| `SOLANA_RPC_URL`      | URL du nœud RPC Solana         |
| `CORS_ALLOWED_ORIGINS`| Domaines autorisés (frontend)  |
