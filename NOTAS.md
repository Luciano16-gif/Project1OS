# NOTAS

Notas internas del proyecto. Estas secciones estaban en la especificacion v0.2 y se movieron aqui para mantener el spec limpio.

## Git workflow (lo que pide el profesor)
Lo que piden NO es complicado: es basicamente un flujo tipo "equipo real", esperemos que el github como tal no tenga problemas como en sistemas de info.

### Ramas
- `main`: siempre estable (lo que "se entrega").
- `develop`: integracion diaria.
- `feat/...`, `fix/...`, `documentation/...`: ramas de trabajo para PRs.

### Como trabajamos (regla simple)
1) Cada tarea vive en una rama propia (ej: `feat/scheduler-edf`).
2) Se abre PR hacia `develop`.
3) Cuando `develop` esta estable, PR de `develop` -> `main`.

### Comandos tipicos
Crear `develop` (una vez):
```bash
git checkout -b develop
git push -u origin develop
```

Crear rama por feature:
```bash
git checkout develop
git pull
git checkout -b feat/gui-queues
git push -u origin feat/gui-queues
```

Esto es mas que nada para tenerlo a la mano porque siempre se me olvida.

### Commits
- Mensajes descriptivos: `feat: ...`, `fix: ...`, `docs: ...`
- Evitar commits gigantes; preferir pequenos y frecuentes.

## Preguntas abiertas (para confirmar con profesores o preparadoras)
- Que rango esperan para `maxProcesosEnMemoria`? (si no lo dan, lo dejamos configurable)
- Que libreria prefieren/permiten para JSON/CSV especificamente?
- Que hacer con un proceso que pierde deadline? (marcar "fallido y terminar", o "fail-soft" y dejarlo correr)

## Estado actual del repo (al 2026-01-18)
- Implementadas estructuras propias: `LinkedQueue<T>`, `SimpleList<T>`, `OrderedList<T>` y `Compare.Comparator<T>`.
- Existe `DataStructuresTest` para pruebas basicas de listas.
- `main()` actual ejecuta `DataStructuresTest.runAll()`; luego se reemplaza por arranque de GUI.

## Nota de mantenimiento
Lo mejor sera que estemos actualizando NOTAS.md y si es necesario ESPECIFICACION_PROYECTO.md a medida que hacemos PRs, para que tengamos un acceso facil a estar al dia. Antes de subir cambios a GitHub hay que asegurarnos de actualizar este archivo con lo necesario. La version la puse por ser fancy realmente, pero puede ser util para llevar un control mas de lo que estamos haciendo (aunq eso ya lo podamos hacer a traves de git).
