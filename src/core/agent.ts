import Anthropic from "@anthropic-ai/sdk";
import type { Message, AgentConfig } from "./types";

const client = new Anthropic();

export class Agent {
  protected history: Message[] = [];
  readonly name: string;
  readonly model: string;
  protected systemPrompt: string;

  constructor(config: AgentConfig) {
    this.name = config.name;
    this.model = config.model;
    this.systemPrompt = config.systemPrompt;
  }

  async run(task: string): Promise<string> {
    this.history.push({ role: "user", content: task });

    console.log(`\n${"─".repeat(60)}`);
    console.log(`🤖  ${this.name}  [${this.model}]`);
    console.log("─".repeat(60));

    const stream = client.messages.stream({
      model: this.model,
      max_tokens: 16000,
      system: this.systemPrompt,
      messages: this.history,
    });

    let text = "";
    for await (const event of stream) {
      if (
        event.type === "content_block_delta" &&
        event.delta.type === "text_delta"
      ) {
        process.stdout.write(event.delta.text);
        text += event.delta.text;
      }
    }
    console.log("\n");

    this.history.push({ role: "assistant", content: text });
    return text;
  }

  clearHistory(): void {
    this.history = [];
  }
}
