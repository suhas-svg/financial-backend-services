/**
 * Global teardown for Jest test suite
 * Runs once after all test suites complete
 */
export default async function globalTeardown() {
  console.log('🧹 Global E2E Test Teardown Starting...');
  
  try {
    // Clean up any global resources
    console.log('🗑️  Cleaning up global test resources...');

    // Reset environment variables
    delete process.env.E2E_TEST_MODE;

    // Log completion
    console.log('✅ Global teardown completed successfully');
  } catch (error) {
    console.error('❌ Global teardown failed:', error);
    // Don't throw error in teardown to avoid masking test failures
  }
}