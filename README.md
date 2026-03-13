# Neon Pong - Cyberpunk Edition v2.2 (Beta 1)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-11%2B-blue)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Version](https://img.shields.io/badge/version-2.2β1-purple)](https://github.com/github-deewhy/pong-neon)
[![Landing Page](https://img.shields.io/badge/web-neonpong.deewhy.ovh-ff69b4)](https://neonpong.deewhy.ovh)

<div align="center">
  <img src="docs/screenshot.png" alt="Neon Pong Screenshot" width="600"/>
  <p><em>⚡ Cyberpunk Arcade Edition · Multiplayer Beta ⚡</em></p>
</div>

## 📋 Software Description

Neon Pong is a visually-stylized, multiplayer-enabled Pong game with a cyberpunk aesthetic. It features:

- **Single-player mode** with 4 AI difficulty levels (Rookie, Veteran, Elite, Legend)
- **Online multiplayer** with persistent rooms and account system (via PocketBase backend)
- **Dynamic visual effects**: particle systems, screen shake, neon gradients, animated stars
- **Custom sound engine** with procedurally generated audio
- **Full keyboard controls** with help overlay
- **Account management**: register, change email/password, delete account

## 🚀 Quick Start

### Prerequisites
- **Java 11 or higher** (required for `java.net.http.HttpClient`)
- Tested on macOS, Windows, and Linux

### Running the game

```bash
# Clone the repository
git clone https://github.com/github-deewhy/pong-neon.git
cd pong-neon

# Compile
javac beta-test/multiplayer/PongGame.java

# Run
java -cp beta-test/multiplayer PongGame