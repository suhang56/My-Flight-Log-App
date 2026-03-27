import { Agent } from "../core/agent";
import type { MissionReport } from "../core/types";

export class LeadAgent extends Agent {
  private planner: Agent;
  private uiux: Agent;
  private developer: Agent;
  private reviewer: Agent;

  constructor(agents: {
    planner: Agent;
    uiux: Agent;
    developer: Agent;
    reviewer: Agent;
  }) {
    super({
      name: "Lead Agent",
      model: "claude-sonnet-4-6",
      systemPrompt: `You are the Lead Agent coordinating an Android development team for the Flight Log App.

Your team:
- Planner (Sonnet): Creates project roadmaps, feature specs, and technical requirements
- UI/UX Designer (Opus): Designs screens, user flows, and component layouts
- Developer (Opus): Writes Kotlin/Jetpack Compose Android code
- Code Reviewer (Opus): Reviews code quality and best practices

Your role:
- Receive missions from the user and coordinate the full development pipeline
- Synthesize the team's outputs into a coherent final summary
- Keep the team aligned on the project goal: a professional Android flight log app for pilots

Be concise, strategic, and action-oriented.`,
    });

    this.planner = agents.planner;
    this.uiux = agents.uiux;
    this.developer = agents.developer;
    this.reviewer = agents.reviewer;
  }

  async dispatch(mission: string): Promise<MissionReport> {
    console.log(`\n${"═".repeat(60)}`);
    console.log(`🎯  MISSION: ${mission}`);
    console.log("═".repeat(60));

    // Step 1: Planning
    console.log("\n📋  STEP 1 / 4  —  Planning");
    const plan = await this.planner.run(
      `Mission: ${mission}\n\nCreate a detailed plan for this mission in the context of the Android Flight Log App. Include features to build, data models, and implementation steps.`
    );

    // Step 2: UI/UX Design
    console.log("\n🎨  STEP 2 / 4  —  UI/UX Design");
    const design = await this.uiux.run(
      `Mission: ${mission}\n\nProject Plan:\n${plan}\n\nDesign the UI/UX for the features in this plan. Describe each screen, layout, navigation flow, and Material Design 3 components to use.`
    );

    // Step 3: Development
    console.log("\n💻  STEP 3 / 4  —  Development");
    const code = await this.developer.run(
      `Mission: ${mission}\n\nProject Plan:\n${plan}\n\nUI/UX Design:\n${design}\n\nImplement the Android code using Kotlin and Jetpack Compose. Write complete, production-quality code.`
    );

    // Step 4: Code Review
    console.log("\n🔍  STEP 4 / 4  —  Code Review");
    const review = await this.reviewer.run(
      `Review the following Android code for the Flight Log App. Identify issues and provide actionable feedback.\n\nCode:\n${code}`
    );

    // Lead synthesizes
    console.log("\n📊  Synthesizing...");
    const summary = await this.run(
      `The team has completed the mission: "${mission}"\n\nWrite a concise summary of what was accomplished, key decisions made, and recommended next steps.`
    );

    return { mission, plan, design, code, review, summary };
  }
}
