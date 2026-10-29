function csvCell(value) {
  let text = String(value ?? '');
  if (/^[=+\-@\t\r]/.test(text)) text = "'" + text;
  return `"${text.replaceAll('"', '""')}"`;
}

export function credentialsCsv(recruits) {
  return '\uFEFF' + [
    ['Matricule', 'Nom complet', 'Actif', 'Mot de passe temporaire'],
    ...recruits.map((user) => [user.username, user.fullName, user.active ? 'Oui' : 'Non', user.temporaryPassword]),
  ].map((row) => row.map(csvCell).join(';')).join('\r\n');
}
