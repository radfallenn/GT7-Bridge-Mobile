# GT7 rAd Telemetry Bridge

Servidor/bridge para rodar em Raspberry Pi e receber telemetria do Gran Turismo 7 no PS5.

## Como funciona

PS5 / GT7 -> UDP -> Raspberry Pi -> HTTP/WebSocket -> Celular/PC

Portas usadas:
- UDP local: 33740
- UDP PS5 heartbeat: 33739
- HTTP/WebSocket: 8787

## Instalação rápida no Raspberry

```bash
cd gt7-rad-telemetry
chmod +x scripts/install.sh scripts/run.sh scripts/install-service.sh
./scripts/install.sh
```

## Rodar manualmente

```bash
PS5_IP=192.168.1.70 ./scripts/run.sh
```

Troque `192.168.1.70` pelo IP do PS5.

Abra no celular:

```text
http://IP_DO_RASPBERRY:8787
```

## Instalar para iniciar junto com o Raspberry

Edite o IP do PS5 em `systemd/gt7-rad-telemetry.service` e rode:

```bash
sudo ./scripts/install-service.sh
```

## Endpoints

- `/` painel de teste
- `/api/health` status do servidor
- `/api/live` último pacote interpretado
- `/api/config` configuração atual
- `/ws` telemetria em tempo real

## Observações

A telemetria do GT7 é uma interface não oficial. Esta base já inclui heartbeat, listener UDP, descriptografia Salsa20 e parser inicial com fallback seguro. Se alguma versão do GT7 mudar campos internos, o app continua mostrando conexão, pacotes e RAW para ajuste.
