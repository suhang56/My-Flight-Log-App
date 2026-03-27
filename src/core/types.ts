export interface Message {
  role: "user" | "assistant";
  content: string;
}

export interface AgentConfig {
  name: string;
  model: string;
  systemPrompt: string;
}

export interface MissionReport {
  mission: string;
  plan: string;
  design: string;
  code: string;
  review: string;
  summary: string;
}
