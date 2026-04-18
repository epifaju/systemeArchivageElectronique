-- Métadonnées avancées : schéma par type documentaire + valeurs JSON sur le document

ALTER TABLE document_types
    ADD COLUMN IF NOT EXISTS custom_fields_schema JSONB;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS custom_metadata JSONB;

CREATE INDEX IF NOT EXISTS idx_documents_custom_metadata ON documents USING GIN (custom_metadata jsonb_path_ops);

-- Exemple : type CONTRAT avec champs métier optionnels (éditable en admin ensuite)
UPDATE document_types
SET custom_fields_schema = '{
  "version": 1,
  "fields": [
    {
      "key": "contractRef",
      "type": "STRING",
      "labelFr": "Référence contrat",
      "labelPt": "Referência do contrato",
      "required": false,
      "maxLength": 80
    },
    {
      "key": "counterparty",
      "type": "STRING",
      "labelFr": "Contrepartie",
      "labelPt": "Contraparte",
      "required": false,
      "maxLength": 200
    },
    {
      "key": "amountEur",
      "type": "NUMBER",
      "labelFr": "Montant (EUR)",
      "labelPt": "Montante (EUR)",
      "required": false
    }
  ]
}'::jsonb
WHERE code = 'CONTRAT';
