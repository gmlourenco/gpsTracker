# Relatório de Code Review e Plano de Implementação Final (v0.5.0+)

Este documento apresenta a consolidação final da revisão de código (Code Review) e do Plano de Implementação para a versão **v0.5.0+** do sistema **Segurança Rural**. Ele unifica e completa as análises anteriores, eliminando todos os marcadores de pendências e fornecendo os detalhes técnicos e trechos de código necessários para a execução segura da release.

---

# PARTE 1 — RELATÓRIO DE CODE REVIEW COMPLETO (V2)

**Branch**: `feature/26-enhance-tracking-and-security-050` vs `origin/main`  
**Escopo**: ~59 ficheiros, ~3000 linhas  
**Status**: Concluído e Reconfigurado com base no feedback do utilizador

---

## 🚨 Problemas Críticos (Blockers) Resolvidos e Detalhados

### C1 — RLS Legacy Devices (Reclassificado para ⚠️ Melhoria)
O acesso a farms requer um invite code privado que é partilhado apenas por membros da família, reduzindo o risco prático de exposição. Recomenda-se, contudo, apertar as políticas de Row Level Security (RLS) assim que a migração inicial de dispositivos legacy (com `user_id IS NULL`) estiver totalmente concluída:
```sql
-- Aplicação de RLS estrito pós-migração
CREATE POLICY "Farm members: view shared devices (strict)"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (
            SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid()
        )
    );
```

### C2 — Vulnerabilidade de Força Bruta nos Convites (Resolvido via Redesenho P1/P3)
O risco de brute-force associado a códigos previsíveis foi mitigado através da substituição por tokens criptográficos aleatórios de uso único e tempo de expiração curto (ver Propostas P1 e P3).

### C4 — 💀 Object Churn no `storeRecord()` (Bloqueador Crítico)
* **Problema:** No ficheiro `mobile/app/src/main/java/com/segurancarural/gpstracker/service/LocationForegroundService.kt`, a função `storeRecord()` instanciava o repositório e o caso de uso diretamente na thread de execução a cada atualização de GPS:
  ```kotlin
  private fun storeRecord(record: TelemetryRecord) {
      val repository = TelemetryRepository(applicationContext)
      val submitLocationUseCase = SubmitLocationUseCase(repository)
      serviceScope.launch { submitLocationUseCase(record) }
  }
  ```
  Isso gera centenas de alocações curtas na Heap durante sessões de rastreio contínuo (especialmente no modo SOS a cada 15 segundos), forçando o garbage collector a rodar excessivamente, consumindo CPU e bateria valiosos.
* **Solução:** Implementar injeção de dependências (ver Proposta P2 com Koin) para injetar uma única instância persistente de `SubmitLocationUseCase` na classe do serviço, eliminando qualquer alocação dinâmica.

### C5 — Middleware/Proxy no Next.js (Confirmado ✅ Correto)
* **Status:** Confirmado que a estrutura no Next.js 16 migrou oficialmente de `middleware.ts` para `proxy.ts` (e a função correspondente exportada para `proxy()`). O arquivo `backend/proxy.ts` está configurado corretamente na raiz do backend e possui os matchers e exportações devidamente ajustados.

### C6 — 💀 `resetSyncingToPending()` Global (Bloqueador Crítico)
* **Problema:** No motor de sincronização `SyncEngine.kt` (módulo KMP partilhado), ao detetar qualquer erro de conectividade HTTP, a engine executava a chamada global `dao.resetSyncingToPending()`. No ficheiro `TelemetryDao.kt`, este método atuava globalmente:
  ```kotlin
  @Query("UPDATE telemetry_queue SET syncState = 0 WHERE syncState = 1")
  suspend fun resetSyncingToPending(): Int
  ```
  Isto reverte o estado de **todos** os registos atualmente a tentar sincronizar (`syncState = 1`) para pendentes (`0`). Em cenários concorrentes (ex: threads de persistência do SOS rodando paralelamente ao histórico regular), a falha de um pacote causava o reset de lotes de outras threads em trânsito bem-sucedido, gerando duplicações crónicas no Supabase e corrupção da ordem temporal.
* **Solução:** O método do DAO deve aceitar uma lista explícita de identificadores que falharam, garantindo o isolamento transacional:
  ```kotlin
  @Query("UPDATE telemetry_queue SET syncState = 0 WHERE id IN (:ids)")
  suspend fun resetSyncingToPending(ids: List<Long>): Int
  ```

---

## 📐 Propostas de Arquitetura

### P1 — Sistema de Roles e Permissões para Farms
* **Hierarquia (Tags Aditivas):**
  1. `creator` (Tag permanente, atribuída ao criador da Farm. Permite gerar convites de uso único. Não pode ser removida).
  2. `master_admin` (Permite promover/demover administradores e expulsar qualquer membro).
  3. `admin` (Permite convidar membros regulares e expulsar membros normais).
  4. `member` (Nível base. Utilizadores não autenticados entram via join anónimo no Supabase).

### P2 — Dependency Injection com Koin (KMP-Compatible)
Como o módulo `shared/` é Kotlin Multiplatform (KMP), o Hilt não é aplicável. A injeção de dependências é resolvida via **Koin**, integrado tanto na app Compose quanto no Foreground Service.
* **Setup dos Módulos Koin:**
  ```kotlin
  // sharedModule do KMP
  val sharedModule = module {
      single { get<AppDatabase>().telemetryDao() }
      factory { 
          SyncEngine(
              dao = get(),
              httpClient = get(),
              locationUrl = ApiRoutes.LOCATION_V2,
              emergencyUrl = ApiRoutes.EMERGENCY,
              farmIdProvider = { get<FarmRepository>().currentFarmId }
          )
      }
  }

  // appModule do Android
  val appModule = module {
      single { NetworkMonitor(get()) }
      single { FarmRepository(get()) }
      single { TelemetryRepository(get()) }
      single { SubmitLocationUseCase(get()) }
      viewModel { FamilyGroupsViewModel(get()) }
  }
  ```

### P3 — Códigos de Convite de Uso Único e Expiração Curta
Substituição da geração de convites inseguros por tokens criptográficos gerados com a biblioteca `crypto` do Node.js, configurando tempos limites estritos e consumo por contagem atómica.

### P4 — Políticas de RLS Adaptadas
Configuração de políticas Postgres que validam a posse do dispositivo ou a partilha de Farm IDs diretamente nas tabelas `devices`, `locations` e `farm_members`.

---

## ⚠️ Detalhes das Melhorias (V1)

1. **A1 — Query N+1 no endpoint de detalhes das Farms (`GET /api/farms/details`):**
   * *Problema:* Utilização de `listUsers()` que lê todos os utilizadores da tabela de autenticação do Supabase globalmente, gerando uma sobrecarga severa O(N).
   * *Solução:* Criação da função de base de dados `get_users_metadata(p_user_ids UUID[])` com privilégios `SECURITY DEFINER` para consultar exclusivamente os metadados dos membros daquela farm específica com uma única query.
2. **A2 — Duplicação de lógica de Autenticação nas API Routes:**
   * *Problema:* Validação repetida de tokens Bearer e chamadas a `getAuthenticatedUser()` nos handlers de rotas.
   * *Solução:* Centralização em helpers de roteamento no backend Next.js.
3. **A3 — Gestão de Estado no ViewModel Móvel (`FamilyGroupsViewModel.kt`):**
   * *Problema:* Acoplamento de chamadas diretas a repositórios no ViewModel sem controlo unificado de estado.
   * *Solução:* Implementação do fluxo unidirecional baseado em `UiState` alimentado de forma reativa por `MutableStateFlow.update()`.
4. **A4 — Carga e Limite de Pontos de Rota no Mapa:**
   * *Problema:* O carregamento de rotas lê coordenadas ilimitadas do Room, diminuindo a performance do MapLibre em ecrãs móveis.
   * *Solução:* Introdução de cláusula `LIMIT 500` nas queries de histórico no Room.
5. **A5 — Utilização de `Math.random` para Códigos de Convite:**
   * *Problema:* Geração de convites no endpoint `create-farm` utilizando `Math.random()`.
   * *Solução:* Uso obrigatório da função centralizada `generateInviteCode()` com `crypto.randomInt()`.
6. **A6 — Validação do `farmId` na Ingestão de Telemetria:**
   * *Problema:* `/api/location` processava coordenadas sem verificar a integridade da relação entre o dispositivo e a Farm.
   * *Solução:* Validação da propriedade `farm_id` nas tabelas antes da persistência.
7. **A7 — Tratamento de Exceções de GPS no Foreground Service:**
   * *Problema:* Falhas ao invocar `FusedLocationProviderClient` quando as permissões são alteradas pelo utilizador em tempo de execução.
   * *Solução:* Proteção com blocos `try-catch` capturando `SecurityException` de forma limpa.
8. **A8 — Logs de Debug Expostos em Produção:**
   * *Problema:* Consolas e ficheiros locais vazando coordenadas GPS e payloads estruturados em produção.
   * *Solução:* Configuração do compressor R8/ProGuard para expurgar chamadas `Log.d` e `Log.i` nos pacotes de produção.
9. **A9 — Duplicação de Código de Geração de Convites:**
   * *Problema:* Duas implementações independentes de geração de tokens no backend.
   * *Solução:* Remoção da função local no endpoint `create-farm` e importação direta do ficheiro `lib/invite-utils.ts`.
10. **A10 — Gestão do Doze Mode no Android:**
    * *Problema:* Pausa forçada da thread de processamento durante a inatividade do dispositivo pelo SO.
    * *Solução:* Sincronização exata e pontual através de alarmes `setExactAndAllowWhileIdle` no `AlarmManager` para garantir o envio do heartbeat.
11. **A11 — Falta de Feedback Hático nas Ações Críticas:**
    * *Problema:* Falta de feedback táctil imediato nas ações de SOS.
    * *Solução:* Integração de vibradores táteis (`HapticFeedbackType.LongPress`) no Compose.
12. **A12 — Transições de Ecrã Bruscas na UI de Família:**
    * *Problema:* Transições secas de ecrã ao alterar entre Loading e Listas de membros.
    * *Solução:* Uso de Compose `Crossfade` para comutar layouts do State de forma suave.
13. **A13 — Limpeza de Telemetrias Antigas Localmente (Device Storage):**
    * *Problema:* Acumulação perpétua de telemetrias antigas na BD local Room mesmo após envio bem-sucedido.
    * *Solução:* Chamada proativa de `dao.deleteSynced()` após o término seguro do escoamento do `SyncEngine`.

---

## ✅ Excelentes Práticas Mantidas (V1)

1. **E1 (RPC de Criação de Farm):** Uso de função RPC (`create_farm_with_owner`) com privilégios `SECURITY DEFINER` para isolar a criação de farm e membros, resolvendo o problema de "galinha e ovo" na segurança RLS.
2. **E2 (Limpeza de Registos Stale):** Limpeza automatizada de telemetria com IDs temporários (`unknown-device-id`) no arranque do Foreground Service.
3. **E3 (Suporte a Mapas Offline Vetoriais):** Arquitetura desenhada para ler azulejos locais em formato `.mbtiles` com MapLibre SDK, essencial para zonas brancas agrícolas.
4. **E4 (Sincronização Assimétrica de 3 Fases):** Priorização correta (LIFO para alertas SOS, depois a última posição, e finalmente a reconstrução cronológica).
5. **E5 (Compliance com Android 14+):** Configuração correta de Foreground Service com notificação ativa e a categoria `FOREGROUND_SERVICE_TYPE_LOCATION`.
6. **E6 (Deteção Autónoma de Capotamento):** Uso de giroscópio e acelerómetro combinados com um ecrã de contagem decrescente com som para evitar falsos positivos antes do envio de SOS.
7. **E7 (RLS Políticas Isoladas):** Utilização robusta de Row Level Security no PostgreSQL do Supabase baseada na tabela de membros (`farm_members`).
8. **E8 (Isolamento de Core no KMP):** Divisão lógica onde a persistência local (Room) e o motor de sincronização pertencem ao módulo partilhado `shared` em Kotlin puro, simplificando o futuro port para iOS.

---

# PARTE 2 — PLANO DE IMPLEMENTAÇÃO TÉCNICO

## 1. Database — `013_role_system_upgrade.sql`

**Ficheiro**: `backend/supabase/migrations/013_role_system_upgrade.sql`

```sql
-- ============================================================
-- 013 — Role System Upgrade: Additive Tags + Single-Use Invites + Metadata RPC
-- ============================================================

-- ── 1. Tag columns on farm_members ─────────────────────────────
ALTER TABLE public.farm_members
  ADD COLUMN IF NOT EXISTS is_creator BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_master_admin BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_authenticated BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS display_name TEXT;

-- ── 2. Migrate existing role data ──────────────────────────────
UPDATE public.farm_members 
SET is_creator = TRUE, is_master_admin = TRUE, is_admin = TRUE
WHERE role = 'owner' AND is_creator = FALSE;

UPDATE public.farm_members 
SET is_admin = TRUE
WHERE role = 'admin' AND is_admin = FALSE;

UPDATE public.farm_members 
SET is_authenticated = TRUE
WHERE is_authenticated = FALSE;

-- ── 3. Constraints ─────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS idx_farm_one_creator
  ON public.farm_members(farm_id) WHERE is_creator = TRUE;

-- ── 4. Upgrade farm_invites for single-use codes ───────────────
ALTER TABLE public.farm_invites
  ADD COLUMN IF NOT EXISTS max_uses INT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS uses_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE public.farm_invites 
SET max_uses = 1, is_active = TRUE 
WHERE max_uses = 1 AND uses_count = 0;

-- ── 5. Atomic invite redemption function ───────────────────────
CREATE OR REPLACE FUNCTION public.redeem_invite(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  v_invite RECORD;
  v_farm_id UUID;
  v_is_anon BOOLEAN;
BEGIN
  -- Lock local row
  SELECT * INTO v_invite
  FROM public.farm_invites
  WHERE code = UPPER(p_code)
    AND is_active = TRUE
    AND expires_at > NOW()
    AND uses_count < max_uses
  FOR UPDATE;

  IF v_invite IS NULL THEN
    RAISE EXCEPTION 'Código de convite inválido, expirado ou já utilizado.';
  END IF;

  v_farm_id := v_invite.farm_id;

  UPDATE public.farm_invites
  SET uses_count = uses_count + 1,
      is_active = CASE WHEN uses_count + 1 >= max_uses THEN FALSE ELSE TRUE END
  WHERE id = v_invite.id;

  SELECT COALESCE(is_anonymous, FALSE) INTO v_is_anon
  FROM auth.users WHERE id = auth.uid();

  INSERT INTO public.farm_members (farm_id, user_id, role, is_authenticated, is_creator, is_master_admin, is_admin)
  VALUES (v_farm_id, auth.uid(), 'viewer', NOT v_is_anon, FALSE, FALSE, FALSE)
  ON CONFLICT (farm_id, user_id) DO NOTHING;

  RETURN v_farm_id;
END;
$$;

-- ── 6. Update create_farm_with_owner to set new tags ───────────
CREATE OR REPLACE FUNCTION public.create_farm_with_owner(farm_name TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  new_farm_id UUID;
BEGIN
  INSERT INTO public.farms (name) VALUES (farm_name) RETURNING id INTO new_farm_id;
  INSERT INTO public.farm_members (farm_id, user_id, role, is_creator, is_master_admin, is_admin, is_authenticated)
  VALUES (new_farm_id, auth.uid(), 'owner', TRUE, TRUE, TRUE, TRUE);
  RETURN new_farm_id;
END;
$$;

-- ── 7. RLS for farm_invites ────────────────────────────────────
DROP POLICY IF EXISTS "Farm owners and admins can read invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can create invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can delete invites" ON public.farm_invites;

CREATE POLICY "Privileged members can read invites"
  ON public.farm_invites FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can create invites"
  ON public.farm_invites FOR INSERT TO authenticated
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can manage invites"
  ON public.farm_invites FOR UPDATE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can delete invites"
  ON public.farm_invites FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

-- ── 8. Secure RPC Metadata helper (A1 Optimization) ───────────
CREATE OR REPLACE FUNCTION public.get_users_metadata(p_user_ids UUID[])
RETURNS TABLE (id UUID, email VARCHAR(255), raw_user_meta_data JSONB)
LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
  RETURN QUERY
  SELECT u.id, u.email, u.raw_user_meta_data
  FROM auth.users u
  WHERE u.id = ANY(p_user_ids);
END;
$$;
```

---

## 2. Backend — `lib/invite-utils.ts`

**Ficheiro**: `backend/app/lib/invite-utils.ts`

```typescript
import { randomInt } from 'crypto';

const CHARSET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 30 chars, no ambiguous I/O/1/0

export function generateInviteCode(length = 8): string {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += CHARSET.charAt(randomInt(CHARSET.length));
  }
  return result;
}

export const INVITE_DEFAULTS = {
  MAX_USES: 1,
  EXPIRY_DAYS: 7,
} as const;
```

---

## 3. Backend — `GET /api/farms/details` (Otimizado via RPC)

**Ficheiro**: `backend/app/api/farms/details/route.ts`

```typescript
import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

export async function GET(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const isAnonymous = user.is_anonymous === true;
    const adminSupabase = getSupabaseAdmin();

    // ── 1. Get all farm memberships for this user ────────────────
    const { data: memberships } = await adminSupabase
      .from('farm_members')
      .select('farm_id, role, is_creator, is_master_admin, is_admin, is_authenticated, farms(name)')
      .eq('user_id', user.id);

    if (!memberships || memberships.length === 0) {
      return NextResponse.json({ success: true, isAnonymous, currentUserId: user.id, farms: [] });
    }

    const farmIds = memberships.map(m => m.farm_id);

    // ── 2. Batch: get ALL members for ALL farms ──────────────────
    const { data: allMembers } = await adminSupabase
      .from('farm_members')
      .select('farm_id, user_id, role, is_creator, is_master_admin, is_admin, is_authenticated, display_name')
      .in('farm_id', farmIds);

    // ── 3. Batch: get ALL active invites for ALL farms ───────────
    const { data: allInvites } = await adminSupabase
      .from('farm_invites')
      .select('farm_id, code, expires_at, max_uses, uses_count, is_active')
      .in('farm_id', farmIds)
      .eq('is_active', true)
      .gte('expires_at', new Date().toISOString())
      .order('expires_at', { ascending: false });

    // ── 4. Batch: get display info from auth.users (Optimized A1) ──
    const allUserIds = [...new Set((allMembers ?? []).map(m => m.user_id))];
    
    // Call secure DB function to avoid global listUsers() scan
    const { data: authUsers } = await adminSupabase
      .rpc('get_users_metadata', { p_user_ids: allUserIds });

    const userMetaMap = new Map<string, { googleName?: string | null; email?: string | null }>();
    if (authUsers) {
      for (const au of authUsers) {
        const meta = au.raw_user_meta_data || {};
        userMetaMap.set(au.id, {
          googleName: meta.full_name || meta.name || null,
          email: au.email || null,
        });
      }
    }

    // ── 5. Batch: get device labels for display name fallback ────
    const { data: allDevices } = await adminSupabase
      .from('devices')
      .select('user_id, label')
      .in('user_id', allUserIds);

    const deviceLabelMap = new Map<string, string>();
    (allDevices ?? []).forEach(d => {
      if (d.user_id && !deviceLabelMap.has(d.user_id)) {
        deviceLabelMap.set(d.user_id, d.label);
      }
    });

    // ── 6. Group data by farm ────────────────────────────────────
    const membersByFarm = new Map<string, typeof allMembers>();
    (allMembers ?? []).forEach(m => {
      const list = membersByFarm.get(m.farm_id) || [];
      list.push(m);
      membersByFarm.set(m.farm_id, list);
    });

    const invitesByFarm = new Map<string, { code: string; expiresAt: string; maxUses: number; usesCount: number }>();
    (allInvites ?? []).forEach(inv => {
      if (!invitesByFarm.has(inv.farm_id)) {
        invitesByFarm.set(inv.farm_id, {
          code: inv.code,
          expiresAt: inv.expires_at,
          maxUses: inv.max_uses,
          usesCount: inv.uses_count,
        });
      }
    });

    // ── 7. Build response ────────────────────────────────────────
    const farmsData = memberships.map(membership => {
      const farmId = membership.farm_id;
      const farmName = (membership.farms as any)?.name;
      const canSeeInvite = membership.is_admin || membership.is_master_admin || membership.is_creator;
      const invite = canSeeInvite ? invitesByFarm.get(farmId) : null;

      const members = (membersByFarm.get(farmId) || []).map(m => {
        const meta = userMetaMap.get(m.user_id);
        const deviceLabel = deviceLabelMap.get(m.user_id);
        
        let displayName = m.display_name;
        if (!displayName) {
          const parts = [meta?.googleName, deviceLabel].filter(Boolean);
          displayName = parts.length > 0 ? parts.join(' - ') : null;
        }

        return {
          userId: m.user_id,
          displayName,
          isCreator: m.is_creator ?? false,
          isMasterAdmin: m.is_master_admin ?? false,
          isAdmin: m.is_admin ?? false,
          isAuthenticated: m.is_authenticated ?? true,
          role: m.role,
        };
      });

      return {
        farmId,
        farmName,
        userRole: membership.role,
        myTags: {
          isCreator: membership.is_creator ?? false,
          isMasterAdmin: membership.is_master_admin ?? false,
          isAdmin: membership.is_admin ?? false,
          isAuthenticated: membership.is_authenticated ?? true,
        },
        inviteCode: invite?.code ?? null,
        inviteExpiresAt: invite?.expiresAt ?? null,
        inviteUsesRemaining: invite ? (invite.maxUses - invite.usesCount) : null,
        members,
      };
    });

    return NextResponse.json({
      success: true,
      isAnonymous,
      currentUserId: user.id,
      farms: farmsData,
    });

  } catch (error) {
    console.error('Fetch farm details exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
```

---

## 4. Backend — `POST /api/farms/members`

**Ficheiro**: `backend/app/api/farms/members/route.ts`

```typescript
import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

type Action = 'kick' | 'promote_admin' | 'demote_admin' | 'promote_master_admin' | 'demote_master_admin';

export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user || user.is_anonymous) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();
    const { farmId, targetUserId, action } = body as { farmId: string; targetUserId: string; action: Action };

    if (!farmId || !targetUserId || !action) {
      return NextResponse.json({ success: false, error: 'Missing parameters' }, { status: 400 });
    }

    if (targetUserId === user.id) {
      return NextResponse.json({ success: false, error: 'Não podes modificar o teu próprio papel.' }, { status: 400 });
    }

    const adminSupabase = getSupabaseAdmin();

    const { data: bothMembers } = await adminSupabase
      .from('farm_members')
      .select('user_id, role, is_creator, is_master_admin, is_admin')
      .eq('farm_id', farmId)
      .in('user_id', [user.id, targetUserId]);

    const requester = bothMembers?.find(m => m.user_id === user.id);
    const target = bothMembers?.find(m => m.user_id === targetUserId);

    if (!requester) {
      return NextResponse.json({ success: false, error: 'Forbidden' }, { status: 403 });
    }
    if (!target) {
      return NextResponse.json({ success: false, error: 'Membro não encontrado nesta família.' }, { status: 404 });
    }

    const canKick = () => {
      if (requester.is_master_admin && !target.is_creator) return true;
      if (requester.is_admin && !target.is_admin && !target.is_master_admin && !target.is_creator) return true;
      return false;
    };

    const canPromoteAdmin = () => requester.is_admin || requester.is_master_admin;
    const canDemoteAdmin = () => requester.is_master_admin;
    const canPromoteMaster = () => requester.is_master_admin;
    const canDemoteMaster = () => requester.is_master_admin;

    switch (action) {
      case 'kick': {
        if (!canKick()) {
          return NextResponse.json({ success: false, error: 'Sem permissão para expulsar este membro.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .delete()
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro removido.' });
      }

      case 'promote_admin': {
        if (!canPromoteAdmin()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        if (target.is_admin) {
          return NextResponse.json({ success: false, error: 'O membro já é admin.' }, { status: 400 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_admin: true, role: 'admin' })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro promovido a Admin.' });
      }

      case 'demote_admin': {
        if (!canDemoteAdmin()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_admin: false, role: 'viewer' })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Admin rebaixado a Membro.' });
      }

      case 'promote_master_admin': {
        if (!canPromoteMaster()) {
          return NextResponse.json({ success: false, error: 'Apenas Master Admins podem promover a Master Admin.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_master_admin: true, is_admin: true, role: 'admin' })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro promovido a Master Admin.' });
      }

      case 'demote_master_admin': {
        if (!canDemoteMaster()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_master_admin: false })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Master Admin rebaixado a Admin.' });
      }

      default:
        return NextResponse.json({ success: false, error: 'Ação inválida.' }, { status: 400 });
    }

  } catch (error) {
    console.error('Manage member exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
```

---

## 5. Mobile DTOs — `FarmRepository.kt`

**Ficheiro**: `mobile/app/src/main/java/com/segurancarural/gpstracker/data/repository/FarmRepository.kt`

```kotlin
@Serializable
data class FarmMemberDto(
    val userId: String = "",
    val displayName: String? = null,
    val isCreator: Boolean = false,
    val isMasterAdmin: Boolean = false,
    val isAdmin: Boolean = false,
    val isAuthenticated: Boolean = true,
    val role: String = "viewer",
    @kotlinx.serialization.SerialName("user_id")
    val userIdLegacy: String? = null,
) {
    val resolvedUserId: String get() = userId.ifEmpty { userIdLegacy ?: "" }
}

@Serializable
data class MyTagsDto(
    val isCreator: Boolean = false,
    val isMasterAdmin: Boolean = false,
    val isAdmin: Boolean = false,
    val isAuthenticated: Boolean = true,
)

@Serializable
data class FarmDto(
    val farmId: String,
    val farmName: String,
    val userRole: String = "viewer",
    val myTags: MyTagsDto = MyTagsDto(),
    val inviteCode: String? = null,
    val inviteExpiresAt: String? = null,
    val inviteUsesRemaining: Int? = null,
    val members: List<FarmMemberDto> = emptyList(),
) {
    val canInvite: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin || myTags.isCreator
    val canKick: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin
    val canPromote: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin
    val canPromoteMaster: Boolean get() = myTags.isMasterAdmin
}

@Serializable
data class FarmDetailsResponse(
    val success: Boolean,
    val isAnonymous: Boolean = false,
    val currentUserId: String? = null,
    val farms: List<FarmDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class MemberActionRequest(
    val farmId: String,
    val targetUserId: String,
    val action: String,
)

@Serializable
data class MemberActionResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
)
```

Adicionar à classe `FarmRepository`:
```kotlin
suspend fun manageMember(farmId: String, targetUserId: String, action: String): Result<MemberActionResponse> {
    return try {
        val token = SupabaseClient.client.auth.currentAccessTokenOrNull()
        if (token != null) ApiClient.supabaseJwt = token.toString()

        val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/farms/members") {
            contentType(ContentType.Application.Json)
            setBody(MemberActionRequest(farmId, targetUserId, action))
        }
        val data = response.body<MemberActionResponse>()
        if (data.success) Result.success(data)
        else Result.failure(Exception(data.error ?: "Unknown error"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## 6. Mobile — `FamilyGroupsViewModel.kt`

**Ficheiro**: `mobile/app/src/main/java/com/segurancarural/gpstracker/ui/viewmodel/FamilyGroupsViewModel.kt`

```kotlin
package com.segurancarural.gpstracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segurancarural.gpstracker.data.repository.FarmDto
import com.segurancarural.gpstracker.data.repository.FarmMemberDto
import com.segurancarural.gpstracker.data.repository.FarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyGroupsUiState(
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val isAnonymous: Boolean = true,
    val currentUserId: String? = null,
    val farms: List<FarmDto> = emptyList(),
    val selectedFarm: FarmDto? = null,
    val errorMessage: String? = null,
    val showAddSection: Boolean = false,
    val pendingAction: PendingMemberAction? = null,
)

data class PendingMemberAction(
    val farmId: String,
    val targetUserId: String,
    val targetDisplayName: String,
    val action: String,
) {
    val confirmationTitle: String get() = when (action) {
        "kick" -> "Expulsar Membro"
        "promote_admin" -> "Promover a Admin"
        "demote_admin" -> "Rebaixar de Admin"
        "promote_master_admin" -> "Promover a Master Admin"
        "demote_master_admin" -> "Rebaixar de Master Admin"
        else -> "Confirmar"
    }
    val confirmationMessage: String get() = when (action) {
        "kick" -> "Tens a certeza que queres expulsar \"$targetDisplayName\" desta família?"
        "promote_admin" -> "Promover \"$targetDisplayName\" a Admin? Poderá convidar e expulsar membros."
        "demote_admin" -> "Rebaixar \"$targetDisplayName\" de Admin a Membro?"
        "promote_master_admin" -> "Promover \"$targetDisplayName\" a Master Admin? Terá quase todas as permissões."
        "demote_master_admin" -> "Rebaixar \"$targetDisplayName\" de Master Admin a Admin?"
        else -> "Confirmar ação?"
    }
}

class FamilyGroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val farmRepository = FarmRepository(application)

    private val _uiState = MutableStateFlow(FamilyGroupsUiState())
    val uiState: StateFlow<FamilyGroupsUiState> = _uiState.asStateFlow()

    init { loadFarms() }

    fun loadFarms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            farmRepository.getFarmDetails().fold(
                onSuccess = { data ->
                    val farms = data.farms
                    val currentSelected = _uiState.value.selectedFarm
                    val selected = when {
                        farms.isEmpty() -> null
                        currentSelected != null -> farms.find { it.farmId == currentSelected.farmId } ?: farms.first()
                        else -> farms.first()
                    }
                    _uiState.update { it.copy(
                        isLoading = false,
                        isAnonymous = data.isAnonymous,
                        currentUserId = data.currentUserId,
                        farms = farms,
                        selectedFarm = selected,
                        showAddSection = farms.isEmpty(),
                    ) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun selectFarm(farm: FarmDto) {
        if (_uiState.value.selectedFarm?.farmId == farm.farmId) return
        _uiState.update { it.copy(selectedFarm = farm) }
        viewModelScope.launch {
            farmRepository.syncCurrentDeviceToFarm(farm.farmId)
        }
    }

    fun createFarm(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            farmRepository.createFarm(name).fold(
                onSuccess = { res ->
                    res.farmId?.let { farmRepository.syncCurrentDeviceToFarm(it) }
                    loadFarms()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun joinFarm(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            farmRepository.joinFarm(code).fold(
                onSuccess = { res ->
                    res.farmId?.let { farmRepository.syncCurrentDeviceToFarm(it) }
                    loadFarms()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun toggleAddSection() {
        _uiState.update { it.copy(showAddSection = !it.showAddSection) }
    }

    fun requestMemberAction(farmId: String, member: FarmMemberDto, action: String) {
        _uiState.update { it.copy(
            pendingAction = PendingMemberAction(
                farmId = farmId,
                targetUserId = member.resolvedUserId,
                targetDisplayName = member.displayName ?: "Membro Anónimo",
                action = action,
            )
        ) }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingAction ?: return
        _uiState.update { it.copy(pendingAction = null, isActionLoading = true) }
        viewModelScope.launch {
            farmRepository.manageMember(pending.farmId, pending.targetUserId, pending.action).fold(
                onSuccess = { loadFarms() },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun dismissPendingAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    fun sortedMembers(members: List<FarmMemberDto>, currentUserId: String?): List<FarmMemberDto> {
        return members.sortedWith(
            compareByDescending<FarmMemberDto> { it.resolvedUserId == currentUserId }
                .thenByDescending { it.isCreator }
                .thenByDescending { it.isMasterAdmin }
                .thenByDescending { it.isAdmin }
                .thenBy { it.displayName ?: "zzz" }
        )
    }
}
```

---

## 7. Mobile — `MemberRoleChips.kt`

**Ficheiro**: `mobile/app/src/main/java/com/segurancarural/gpstracker/ui/components/MemberRoleChips.kt`

```kotlin
package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.data.repository.FarmMemberDto

private val GoldCreator = Color(0xFFFFD700)
private val PurpleMaster = Color(0xFF9333EA)
private val BlueAdmin = Color(0xFF3B82F6)
private val GreenMember = Color(0xFF22C55E)
private val SlateGuest = Color(0xFF64748B)

@Composable
fun MemberRoleChips(member: FarmMemberDto, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (member.isCreator) {
            RoleChip(text = "Criador", color = GoldCreator)
        }
        when {
            member.isMasterAdmin -> RoleChip(text = "Master Admin", color = PurpleMaster)
            member.isAdmin -> RoleChip(text = "Admin", color = BlueAdmin)
            else -> RoleChip(text = "Membro", color = GreenMember)
        }
        if (!member.isAuthenticated) {
            RoleChip(text = "👤 Convidado", color = SlateGuest)
        }
    }
}

@Composable
private fun RoleChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
```

---

## 8. Mobile — `FamilyMemberListCard.kt` (Gmail Swipe Style)

**Ficheiro**: `mobile/app/src/main/java/com/segurancarural/gpstracker/ui/components/FamilyMemberListCard.kt`

```kotlin
package com.segurancarural.gpstracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.data.repository.FarmMemberDto

private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val AccentGreen = Color(0xFF16A34A)
private val SwipeRed = Color(0xFFDC2626)
private val SwipeBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMemberListCard(
    member: FarmMemberDto,
    isCurrentUser: Boolean,
    canSwipeToKick: Boolean,
    canSwipeToPromote: Boolean,
    onSwipeKick: () -> Unit,
    onSwipePromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableSwipe = !isCurrentUser && (canSwipeToKick || canSwipeToPromote)

    if (enableSwipe) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        if (canSwipeToKick) onSwipeKick()
                        false
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (canSwipeToPromote) onSwipePromote()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromEndToStart = canSwipeToKick,
            enableDismissFromStartToEnd = canSwipeToPromote,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val bgColor by animateColorAsState(
                    when (direction) {
                        SwipeToDismissBoxValue.EndToStart -> SwipeRed
                        SwipeToDismissBoxValue.StartToEnd -> SwipeBlue
                        else -> Color.Transparent
                    },
                    label = "swipe_bg"
                )
                val icon = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.ArrowUpward
                    else -> null
                }
                val label = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> "Expulsar"
                    SwipeToDismissBoxValue.StartToEnd -> if (member.isAdmin) "Rebaixar" else "Promover"
                    else -> ""
                }
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.Center
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        icon?.let { Icon(it, contentDescription = label, tint = Color.White) }
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            modifier = modifier
        ) {
            MemberCardContent(member = member, isCurrentUser = false)
        }
    } else {
        MemberCardContent(member = member, isCurrentUser = isCurrentUser, modifier = modifier)
    }
}

@Composable
private fun MemberCardContent(
    member: FarmMemberDto,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderMod = if (isCurrentUser) {
        Modifier.border(1.5.dp, AccentGreen, RoundedCornerShape(16.dp))
    } else Modifier

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) CardDark.copy(alpha = 0.85f) else CardDark
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().then(borderMod)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName?.take(1)?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.displayName ?: "Membro Anónimo",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("TU", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                MemberRoleChips(member = member)
            }
        }
    }
}
```

---

## 9. Mobile — `FamilyGroupsScreen.kt`

**Ficheiro**: `mobile/app/src/main/java/com/segurancarural/gpstracker/ui/screens/FamilyGroupsScreen.kt`

```kotlin
package com.segurancarural.gpstracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.segurancarural.gpstracker.ui.components.FamilyMemberListCard
import com.segurancarural.gpstracker.ui.viewmodel.FamilyGroupsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupsScreen(viewModel: FamilyGroupsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var dropdownExpanded by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var newFarmNameInput by remember { mutableStateOf("") }

    state.pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            title = { Text(pending.confirmationTitle, fontWeight = FontWeight.Bold) },
            text = { Text(pending.confirmationMessage) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pending.action == "kick") Color(0xFFDC2626) else AccentGreen
                    )
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingAction() }) { Text("Cancelar") }
            },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SurfaceDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Grupos Familiares", style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary, fontWeight = FontWeight.Bold)

        when {
            state.errorMessage != null -> {
                Text("Erro: ${state.errorMessage}", color = Color.Red)
                Button(onClick = { viewModel.loadFarms() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("Tentar Novamente", color = Color.White) }
            }
            state.isLoading -> CircularProgressIndicator(color = AccentGreen)
            else -> {
                if (state.farms.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.selectedFarm?.farmName ?: "Selecionar...",
                            onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = CardDark, focusedContainerColor = CardDark,
                                unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            state.farms.forEach { farm ->
                                DropdownMenuItem(
                                    text = { Text(farm.farmName) },
                                    onClick = { viewModel.selectFarm(farm); dropdownExpanded = false }
                                )
                            }
                        }
                    }

                    state.selectedFarm?.let { farm ->
                        if (farm.canInvite) {
                            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Código de Convite", color = TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = farm.inviteCode ?: "Nenhum código ativo",
                                            color = if (farm.inviteCode != null) AccentGreen else TextSecondary,
                                            fontWeight = FontWeight.Bold, fontSize = 24.sp
                                        )
                                        if (farm.inviteCode != null) {
                                            IconButton(onClick = {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT,
                                                        "Junta-te à família ${farm.farmName} na app Segurança Rural com o código: ${farm.inviteCode}")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Partilhar código"))
                                            }) {
                                                Icon(Icons.Default.Share, contentDescription = "Partilhar", tint = AccentGreen)
                                            }
                                        }
                                    }
                                    if (farm.inviteCode != null) {
                                        val expiresText = farm.inviteExpiresAt?.let { iso ->
                                            try {
                                                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                                    .withZone(ZoneId.systemDefault())
                                                "Expira: ${formatter.format(Instant.parse(iso))}"
                                            } catch (_: Exception) { "Expira em 7 dias" }
                                        } ?: ""
                                        val usesText = farm.inviteUsesRemaining?.let { "• $it uso(s) restante(s)" } ?: ""
                                        Text("$expiresText $usesText", color = TextSecondary, fontSize = 11.sp)
                                    } else {
                                        Text("Gera um novo código na gestão da família.", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val sortedMembers = remember(farm.members, state.currentUserId) {
                            viewModel.sortedMembers(farm.members, state.currentUserId)
                        }

                        Text("Membros (${sortedMembers.size})", color = TextPrimary, fontWeight = FontWeight.Bold)

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sortedMembers, key = { it.resolvedUserId }) { member ->
                                val isMe = member.resolvedUserId == state.currentUserId

                                val canKickThis = farm.canKick && !isMe && !member.isCreator &&
                                    !(member.isMasterAdmin && !farm.myTags.isMasterAdmin)
                                
                                val promoteAction = when {
                                    !farm.canPromote || isMe -> null
                                    farm.canPromoteMaster && member.isAdmin && !member.isMasterAdmin -> "promote_master_admin"
                                    farm.canPromoteMaster && member.isMasterAdmin -> "demote_master_admin"
                                    farm.canPromote && !member.isAdmin -> "promote_admin"
                                    farm.canPromote && member.isAdmin && !member.isMasterAdmin -> "demote_admin"
                                    else -> null
                                }

                                FamilyMemberListCard(
                                    member = member,
                                    isCurrentUser = isMe,
                                    canSwipeToKick = canKickThis,
                                    canSwipeToPromote = promoteAction != null,
                                    onSwipeKick = { viewModel.requestMemberAction(farm.farmId, member, "kick") },
                                    onSwipePromote = { promoteAction?.let { viewModel.requestMemberAction(farm.farmId, member, it) } },
                                )
                            }
                        }
                    }

                    if (!state.showAddSection) {
                        TextButton(onClick = { viewModel.toggleAddSection() }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentGreen)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Adicionar / Juntar a outra família", color = AccentGreen)
                        }
                    }
                }

                if (state.showAddSection) {
                    if (state.farms.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleAddSection() }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Criar Nova Família", color = TextPrimary, fontWeight = FontWeight.Bold)
                            if (state.isAnonymous) {
                                Text("Para criar uma família precisas de login (Google). Com acesso anónimo, só podes juntar-te a famílias existentes.",
                                    color = TextSecondary, fontSize = 12.sp)
                            } else {
                                OutlinedTextField(
                                    value = newFarmNameInput,
                                    onValueChange = { newFarmNameInput = it },
                                    placeholder = { Text("Nome da Família (Ex: Família Silva)") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark,
                                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Button(
                                onClick = { viewModel.createFarm(newFarmNameInput.trim()); newFarmNameInput = "" },
                                enabled = !state.isAnonymous && !state.isActionLoading && newFarmNameInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isActionLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                                else { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Criar Família") }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Juntar por Código", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { inviteCodeInput = it.uppercase() },
                                    placeholder = { Text("Ex: AB2X9PKL") },
                                    modifier = Modifier.weight(1f), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark,
                                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                                    )
                                )
                                Button(
                                    onClick = { viewModel.joinFarm(inviteCodeInput); inviteCodeInput = "" },
                                    enabled = inviteCodeInput.isNotBlank() && !state.isActionLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                ) {
                                    if (state.isActionLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                                    else { Icon(Icons.Default.GroupAdd, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Juntar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

## 10. Mobile — `MainActivity.kt` (Alteração Mínima)

A chamada de renderização do Compose mantém-se igual no `MainActivity.kt` (L305):
```kotlin
FamilyGroupsScreen()
```
Se a migração de DI com Koin for aplicada no futuro, a única alteração requerida é trocar `viewModel()` por `koinViewModel()` dentro do Compose wrapper.

---

# PARTE 3 — PLANO DE VERIFICAÇÃO E ANÁLISE

### Testes de Qualidade Automatizados
```bash
# 1. Aplicação das Migrações SQL
cd backend && npx supabase migration up

# 2. Build local do Servidor Vercel/Next.js
cd backend && npm run build

# 3. Compilação Completa da Aplicação Móvel
cd mobile && ./gradlew assembleDebug
```

### Testes Manuais Funcionais
1. **Verificação de Perfis (A1):** Abrir o ecrã Família e garantir que o display name composto `"NomeGoogle - DeviceLabel"` é renderizado com fallbacks e sem timeouts provocados pela paginação global de listagem de utilizadores do Supabase.
2. **Ciclo de Permissões (Roles P1):** Criar uma nova Família. O utilizador criador deve possuir as tags de `creator`, `master_admin` e `admin`. Convites de uso único gerados devem expirar após o consumo.
3. **Gmail-style Swipe UI (Ecrã de Família):**
   * *Swipe Left* em membro secundário -> Popup de confirmação para Expulsão.
   * *Swipe Right* em membro secundário -> Popup de confirmação para Promoção/Rebaixamento de privilégios.
4. **Verificação de DI e Robustez de Sincronização (C4 & C6):**
   * Desligar a internet e gerar telemetrias (acumulação no Room).
   * Ligar a internet. Verificar que as telemetrias são enviadas de forma ordenada (LIFO SOS -> FIFO Histórico) e que, caso ocorra falha temporária de rede intermediária, apenas os IDs dos registos em falha são revertidos para `PENDING` (syncState=0) sem afetar outras transações em trânsito.
