import { createPupAccountRealtime } from "./pup-account-realtime.js";

/**
 * Framework-neutral coordinator for the future Puppy Clicker website.
 *
 * transport must implement:
 *   accountStatus() -> authenticated Pup Account account-status response
 *   readCloudSave() -> authenticated cloud-save-read response
 *   writeCloudSave({ expectedRevision, saveSchema, saveData }) -> cloud-save-write response
 *
 * The transport intentionally owns authentication/signing. No service-role or secret key belongs
 * in browser code.
 */
export class PupAccountSync {
  constructor({
    transport,
    supabaseUrl,
    publishableKey,
    onState = () => {},
    onStatus = () => {},
  }) {
    if (!transport) throw new Error("Pup Account transport is required");
    this.transport = transport;
    this.supabaseUrl = supabaseUrl;
    this.publishableKey = publishableKey;
    this.onState = onState;
    this.onStatus = onStatus;
    this.realtime = null;
    this.account = null;
    this.guildRole = null;
    this.guildVerifiedAt = null;
    this.revision = 0;
    this.generation = 0;
    this.saveData = null;
    this.refreshing = null;
  }

  async start() {
    const status = await this.refreshAccount();
    if (!status?.realtimeTopic) throw new Error("Pup Account realtime topic is missing");

    await this.refresh(true);
    this.realtime?.stop();
    this.realtime = createPupAccountRealtime({
      supabaseUrl: this.supabaseUrl,
      publishableKey: this.publishableKey,
      realtimeTopic: status.realtimeTopic,
      onStatus: this.onStatus,
      onChanged: (event) => {
        const eventRevision = Number(event?.revision ?? 0);
        if (event.kind === "save" && eventRevision > 0 && eventRevision <= this.revision) return;
        if (event.kind === "save") {
          void this.refresh(false);
          return;
        }
        void this.refreshAccount().then(() => this.refresh(false));
      },
    });

    return this.snapshot();
  }

  async refreshAccount() {
    const status = await this.transport.accountStatus();
    if (!status?.account || !status?.realtimeTopic) {
      throw new Error("Pup Account status response is incomplete");
    }
    this.account = status.account;
    this.guildRole = status.guildRole ?? null;
    this.guildVerifiedAt = status.guildVerifiedAt ?? null;
    const cloud = status.cloudSave ?? null;
    if (cloud) {
      this.revision = Math.max(this.revision, Number(cloud.revision ?? 0));
      this.generation = Math.max(this.generation, Number(cloud.generation ?? 0));
    }
    this.onState(this.snapshot());
    return status;
  }

  async refresh(force = false) {
    if (this.refreshing && !force) return this.refreshing;
    this.refreshing = (async () => {
      const response = await this.transport.readCloudSave();
      const save = response?.save ?? null;
      if (!save) {
        this.revision = 0;
        this.generation = 0;
        this.saveData = null;
      } else if (force || Number(save.revision) >= this.revision) {
        this.revision = Number(save.revision);
        this.generation = Number(save.generation);
        this.saveData = save.saveData;
      }
      const snapshot = this.snapshot();
      this.onState(snapshot);
      return snapshot;
    })();

    try {
      return await this.refreshing;
    } finally {
      this.refreshing = null;
    }
  }

  async write(saveData, saveSchema = 1) {
    const result = await this.transport.writeCloudSave({
      expectedRevision: this.revision,
      saveSchema,
      saveData,
    });
    this.revision = Number(result.revision);
    this.generation = Number(result.generation ?? this.generation);
    this.saveData = saveData;
    const snapshot = this.snapshot();
    this.onState(snapshot);
    return snapshot;
  }

  snapshot() {
    return {
      account: this.account,
      guildRole: this.guildRole,
      guildVerifiedAt: this.guildVerifiedAt,
      revision: this.revision,
      generation: this.generation,
      saveData: this.saveData,
    };
  }

  stop() {
    this.realtime?.stop();
    this.realtime = null;
  }
}
