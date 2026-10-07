#!/usr/bin/env bash
# Закрывает снаружи служебные порты k3s, которые слушают все интерфейсы:
#   6443  — API кластера;
#   10250 — kubelet (exec/logs подов);
#   10256 — healthz kube-proxy.
# Доступ остаётся с самого хоста (интерфейс lo: туда же идут соединения на собственный публичный
# IP) и из подов (10.42.0.0/16: metrics-server → kubelet, coredns → API).
#
# Отдельное правило, а не UFW: на сервере живут confeek и crm, и включать или перестраивать UFW
# ради TutorCraft нельзя (docs/deployment-ip.md §7). Правило точечное и чужие порты не трогает.
#
# Ставится systemd-юнитом tutorcraft-k3s-guard.service (bootstrap-ip.sh) и применяется до старта
# k3s при каждой загрузке. Идемпотентен: повторный запуск правило не дублирует.
set -euo pipefail

readonly PORTS="6443,10250,10256"
readonly POD_CIDR="10.42.0.0/16"
readonly TAG="tutorcraft-k3s-guard"

rule4=(INPUT -p tcp -m multiport --dports "$PORTS" ! -i lo ! -s "$POD_CIDR" -m comment --comment "$TAG" -j DROP)
iptables -C "${rule4[@]}" 2>/dev/null || iptables -I "${rule4[@]}"

# API-сервер слушает и IPv6 (::). Кластер у нас только IPv4, поэтому снаружи закрываем всё, кроме lo.
if command -v ip6tables >/dev/null && ip6tables -L INPUT -n >/dev/null 2>&1; then
  rule6=(INPUT -p tcp -m multiport --dports "$PORTS" ! -i lo -m comment --comment "$TAG" -j DROP)
  ip6tables -C "${rule6[@]}" 2>/dev/null || ip6tables -I "${rule6[@]}"
fi
