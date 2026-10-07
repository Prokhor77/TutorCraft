# ADR-013: Прод в k3s на общем сервере вместо docker compose
Статус: принято · 2026-10-07 · заменяет часть ADR-001 про «Docker + Compose; Kubernetes — позже» (для прода)

**Контекст.** Прод работал как `docker-compose.prod.yml` с оверлеем `docker-compose.ip.yml` на сервере
91.149.179.186. Сервер общий: 7.9 ГБ RAM, рядом в Docker живут confeek и crm, порты 80/443 держит
host-nginx. У compose-схемы три слабых места:
- пользователь деплоя состоял в группе `docker`, а это root на всём сервере, включая контейнеры соседей;
- порядок старта и ожидание готовности держались на `depends_on` и самописном цикле `docker inspect` в workflow;
- откат требовал правки `.env` (`TUTORCRAFT_IMAGE_TAG`) руками.

**Решение.**
- Весь стек TutorCraft, включая PostgreSQL, MongoDB, Redis и Kafka, работает в однонодовом k3s на
  том же сервере, в namespace `tutorcraft`. Манифесты — Kustomize в `infra/k8s/app/`. Кластерные
  объекты (namespace, PersistentVolume, RBAC) лежат в `infra/k8s/cluster/` и применяются root один раз.
- k3s без Traefik и ServiceLB: 80/443 остаются за host-nginx. nginx проксирует в **закреплённые
  ClusterIP** (`10.43.0.100–102`): они доступны с хоста через kube-proxy и не открывают ни одного
  порта на интерфейсах. NodePort и hostPort отвергнуты: первый слушает все адреса, второй мешает
  раскатке без простоя.
- Служебные порты k3s (6443, 10250, 10256) снаружи закрыты точечным правилом iptables
  (`tutorcraft-k3s-guard`), flannel работает в режиме `host-gw`. UFW не трогаем из-за соседей.
- Данные лежат в статических hostPath-PersistentVolume на `/opt/tutorcraft/data/*` с `Retain`.
  local-path отвергнут: со случайными именами каталогов сложнее перенос и бэкап.
- Деплой: CI рендерит манифесты с неизменяемым тегом `sha-<commit>` и проверяет их kubeconform.
  На сервере `infra/k8s/apply.sh` под ServiceAccount с ролью `admin` только в namespace применяет
  их в два этапа: хранилища и Job топиков, затем приложения. Pod Security `baseline` на namespace
  не даёт этой учётке получить хост через hostPath или privileged.
- Секреты по-прежнему в `/opt/tutorcraft/.env`. `apply.sh` превращает его в Secret, а хэш файла
  кладёт в аннотацию подов, поэтому правка `.env` перезапускает поды.
- Прод-compose удалён. Dev-`docker-compose.yml` и e2e на нём не меняются.

**Последствия.**
- +0.5–0.7 ГБ RAM на сам k3s при неизменных лимитах подов, итого ~3 ГБ в покое. Swap 4 ГБ
  обязателен (docs/deployment-ip.md §3.1).
- core-api и media-worker раскатываются стратегией `Recreate`: два пода core-api по 1.5 ГБ сервер
  не выдержит. Простой ~1–2 минуты, как раньше при `up -d`. Когда появится память, можно перейти
  на RollingUpdate: миграции уже expand/contract.
- Откат приложения — `kubectl rollout undo`. Удалённые из манифестов объекты сами не удаляются
  (prune не используется), их удаляют вручную.
- Переезд с compose — разовая процедура с простоем: дампы, копия файлов, восстановление
  (docs/deployment-ip.md §3.3). Kafka не переносится: неопубликованные события остаются в `outbox`.
