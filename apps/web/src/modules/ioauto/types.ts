export type BillingSnapshot = {
    hasSubscription: boolean;
    planName: string;
    status: string;
    amountCents: number | null;
    currency: string;
    billingInterval: string;
    currentPeriodEnd: string | null;
    cancelAtPeriodEnd: boolean;
    provider: string;
    providerCustomerId: string;
    providerSubscriptionId: string;
};

export type DashboardResponse = {
    companyName: string;
    vehicleCount: number;
    featuredCount: number;
    publicationCount: number;
    leadCount: number;
    connectedIntegrations: number;
    billing: BillingSnapshot;
    periodFilter: {
        preset: string;
        from: string;
        to: string;
    };
    leadVsSales: Array<{
        date: string;
        label: string;
        leads: number;
        sales: number;
    }>;
    salesBySeller: Array<{
        sellerId: string | null;
        sellerName: string;
        totalSales: number;
    }>;
    leadSources: Array<{ key: string; label: string; total: number }>;
    recentVehicles: Array<{
        id: string;
        title: string;
        priceCents: number | null;
        status: string;
        updatedAt: string | null;
        publicationCount: number;
    }>;
    recentConversations: Array<{
        id: string;
        contactName: string;
        lastMessage: string;
        lastAt: string | null;
        sourcePlatform: string;
    }>;
};

export type VehiclePublication = {
    id: string;
    providerKey: string;
    providerName: string;
    status: string;
    externalUrl: string | null;
};

export type VehicleRecord = {
    id: string;
    stockNumber: string | null;
    title: string;
    brand: string;
    model: string;
    version: string | null;
    modelYear: number | null;
    manufactureYear: number | null;
    priceCents: number | null;
    mileage: number | null;
    transmission: string | null;
    fuelType: string | null;
    bodyType: string | null;
    color: string | null;
    plateFinal: string | null;
    city: string | null;
    state: string | null;
    featured: boolean;
    status: string;
    description: string | null;
    coverImageUrl: string | null;
    gallery: string[];
    optionals: string[];
    publications: VehiclePublication[];
    updatedAt: string | null;
};

export type IntegrationRecord = {
    providerKey: string;
    displayName: string;
    status: string;
    endpointUrl: string | null;
    accountName: string | null;
    username: string | null;
    hasApiToken: boolean;
    hasWebhookSecret: boolean;
    supportsPublication: boolean;
    lastSyncAt: string | null;
    lastError: string | null;
    settings: Record<string, string>;
};

export type PublicationRecord = {
    id: string;
    vehicleId: string;
    vehicleTitle: string;
    providerKey: string;
    providerName: string;
    status: string;
    externalUrl: string | null;
    lastError: string | null;
    publishedAt: string | null;
    updatedAt: string | null;
};

export type ConversationRecord = {
    id: string;
    phone: string;
    displayName: string | null;
    photoUrl: string | null;
    sourcePlatform: string | null;
    sourceReference: string | null;
    status: "NEW" | "IN_PROGRESS";
    assignedTeamId: string | null;
    assignedTeamName: string | null;
    assignedUserId: string | null;
    assignedUserName: string | null;
    lastMessage: string | null;
    lastAt: string | null;
    lastMessageFromMe: boolean | null;
    lastMessageStatus: string | null;
    lastMessageType: string | null;
    sessionId?: string | null;
    arrivedAt?: string | null;
    firstResponseAt?: string | null;
    completedAt?: string | null;
    classificationResult?: string | null;
    classificationLabel?: string | null;
    saleCompleted?: boolean | null;
    soldVehicleId?: string | null;
    soldVehicleTitle?: string | null;
    saleCompletedAt?: string | null;
    latestCompletedAt?: string | null;
    latestCompletedClassificationResult?: string | null;
    latestCompletedClassificationLabel?: string | null;
    latestCompletedSaleCompleted?: boolean | null;
    latestCompletedSoldVehicleId?: string | null;
    latestCompletedSoldVehicleTitle?: string | null;
    labels?: Array<{
        id: string;
        title: string;
        color?: string | null;
    }> | null;
};

export type ConversationMessage = {
    id: string;
    conversationId: string;
    phone: string;
    text: string | null;
    type: string | null;
    imageUrl?: string | null;
    stickerUrl?: string | null;
    videoUrl?: string | null;
    audioUrl?: string | null;
    documentUrl?: string | null;
    documentName?: string | null;
    fromMe: boolean;
    status?: string | null;
    createdAt: string;
};

export type SignupStatus = {
    intentId: string;
    status: string;
    message: string;
    accessReady: boolean;
    loginEmail: string;
    companyName: string;
};
