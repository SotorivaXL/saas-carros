export const AI_COMMUNICATION_STYLE_PRESETS = [
    {
        label: "Consultivo e Acolhedor",
        value: "Consultivo e Acolhedor",
        example: "Exemplo: Como posso te ajudar hoje? Me avise se tiver alguma dúvida ou se quiser saber mais sobre nossos serviços.",
    },
    {
        label: "Neutro e Equilibrado",
        value: "Neutro e Equilibrado",
        example: "Exemplo: Entendido. Vou te passar as informações de forma objetiva e clara.",
    },
    {
        label: "Formal e Institucional",
        value: "Formal e Institucional",
        example: "Exemplo: Prezado(a), segue o detalhamento solicitado com os próximos passos.",
    },
] as const;

export const AI_COMMUNICATION_STYLE_CUSTOM_OPTION = {
    label: "Outro",
    value: "__custom__",
    example: "Defina uma forma de comunicação personalizada para este agente.",
} as const;

export const AI_COMMUNICATION_STYLE_OPTIONS = [
    ...AI_COMMUNICATION_STYLE_PRESETS,
    AI_COMMUNICATION_STYLE_CUSTOM_OPTION,
] as const;

export const AI_AGENT_PROFILE_PRESETS = [
    "Vendedor",
    "SDR",
    "Suporte",
    "Onboarding",
    "Recepcionista",
] as const;

export const AI_AGENT_PROFILE_CUSTOM_OPTION = "Outro" as const;

export const AI_AGENT_PROFILE_OPTIONS = [
    ...AI_AGENT_PROFILE_PRESETS,
    AI_AGENT_PROFILE_CUSTOM_OPTION,
] as const;
