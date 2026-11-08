import { type SubmitEvent, useState } from 'react';

import { apiRequest } from '../api/client';
import type { FamilySettings } from '../api/types';
import { useApiData } from '../api/useApiData';
import { ErrorAlert, LoadingState, SuccessMessage } from '../components/Feedback';
import { CARD_OFFSET_LABEL } from '../labels';

const TIMEZONES = [
  'America/Fortaleza',
  'America/Recife',
  'America/Bahia',
  'America/Sao_Paulo',
  'America/Belem',
  'America/Manaus',
  'America/Cuiaba',
  'America/Porto_Velho',
  'America/Boa_Vista',
  'America/Rio_Branco',
  'America/Noronha',
];

interface FormState {
  timezone: string;
  averageWindowMonths: string;
  prudentMarginPercent: string;
  cardPaymentOffsetMonths: string;
}

function toForm(settings: FamilySettings): FormState {
  return {
    timezone: settings.timezone,
    averageWindowMonths: String(settings.averageWindowMonths),
    prudentMarginPercent: settings.prudentMarginPercent,
    cardPaymentOffsetMonths: String(settings.cardPaymentOffsetMonths),
  };
}

export function SettingsPage() {
  const { data: settings, error: loadError, setData, reload } = useApiData<FamilySettings>(
    '/api/v1/family/settings',
  );
  // null = sem edição pendente; o formulário mostra os valores salvos.
  const [draft, setDraft] = useState<FormState | null>(null);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (!settings) {
    return loadError ? <ErrorAlert error={loadError} /> : <LoadingState label="Carregando configurações…" />;
  }
  const form = draft ?? toForm(settings);

  const update = (field: keyof FormState, value: string) => {
    setDraft({ ...form, [field]: value });
  };

  const handleSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    setSuccess(null);
    // A margem segue como texto; o servidor valida e arredonda. O React não calcula valores.
    apiRequest<FamilySettings>('/api/v1/family/settings', {
      method: 'PATCH',
      json: {
        timezone: form.timezone,
        averageWindowMonths: Number(form.averageWindowMonths),
        prudentMarginPercent: form.prudentMarginPercent.trim(),
        cardPaymentOffsetMonths: Number(form.cardPaymentOffsetMonths),
        version: settings.version,
      },
    })
      .then((data) => {
        setData(data);
        setDraft(null);
        setSuccess('Configurações salvas.');
      })
      .catch((error: unknown) => {
        setSaveError(error);
        reload();
      })
      .finally(() => {
        setSaving(false);
      });
  };

  return (
    <section aria-labelledby="titulo-config">
      <h1 id="titulo-config" className="h3">
        Configurações da família
      </h1>
      <SuccessMessage message={success} />
      <ErrorAlert error={saveError} />
      <form onSubmit={handleSubmit} className="row g-3">
        <div className="col-12 col-md-6">
          <label htmlFor="cfg-moeda" className="form-label">
            Moeda
          </label>
          <input id="cfg-moeda" className="form-control" value="BRL — Real" readOnly />
        </div>
        <div className="col-12 col-md-6">
          <label htmlFor="cfg-tz" className="form-label">
            Fuso horário
          </label>
          <input
            id="cfg-tz"
            className="form-control"
            list="cfg-tz-opcoes"
            required
            value={form.timezone}
            onChange={(e) => {
              update('timezone', e.target.value);
            }}
          />
          <datalist id="cfg-tz-opcoes">
            {TIMEZONES.map((tz) => (
              <option key={tz} value={tz} />
            ))}
          </datalist>
        </div>
        <div className="col-12 col-md-4">
          <label htmlFor="cfg-janela" className="form-label">
            Janela da média (meses completos)
          </label>
          <input
            id="cfg-janela"
            type="number"
            min={1}
            max={12}
            className="form-control"
            value={form.averageWindowMonths}
            onChange={(e) => {
              update('averageWindowMonths', e.target.value);
            }}
          />
        </div>
        <div className="col-12 col-md-4">
          <label htmlFor="cfg-margem" className="form-label">
            Margem prudente (%)
          </label>
          <input
            id="cfg-margem"
            inputMode="decimal"
            className="form-control"
            aria-describedby="cfg-margem-ajuda"
            value={form.prudentMarginPercent}
            onChange={(e) => {
              update('prudentMarginPercent', e.target.value);
            }}
          />
          <div id="cfg-margem-ajuda" className="form-text">
            Use ponto como separador decimal, por exemplo 10.00.
          </div>
        </div>
        <div className="col-12 col-md-4">
          <label htmlFor="cfg-cartao" className="form-label">
            Pagamento de novas compras no cartão
          </label>
          <select
            id="cfg-cartao"
            className="form-select"
            value={form.cardPaymentOffsetMonths}
            onChange={(e) => {
              update('cardPaymentOffsetMonths', e.target.value);
            }}
          >
            {[0, 1, 2, 3].map((value) => (
              <option key={value} value={String(value)}>
                {CARD_OFFSET_LABEL[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Salvando…' : 'Salvar'}
          </button>
        </div>
      </form>
    </section>
  );
}
