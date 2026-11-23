/** A documentation example: runs against the packaged library and throws when its result is wrong. */
export interface Example {
    readonly name: string;
    run(log: (line: string) => void): Promise<void>;
}
